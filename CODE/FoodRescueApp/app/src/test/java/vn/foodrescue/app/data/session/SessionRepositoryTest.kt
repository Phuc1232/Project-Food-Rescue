package vn.foodrescue.app.data.session

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import vn.foodrescue.app.data.remote.AuthApi
import vn.foodrescue.app.ui.auth.*
import java.util.concurrent.TimeUnit

class SessionRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var api: AuthApi
    private lateinit var store: MemoryTokenStore
    private lateinit var repository: SessionRepository
    private val profile = """{"id":12,"name":"Bao Nguyen","email":"bao@example.com","phone":null,"role":"KHACH_HANG"}"""
    private val session get() = """{"accessToken":"new-access","refreshToken":"new-refresh","expiresInSeconds":900,"user":$profile}"""

    @Before fun setup() {
        server = MockWebServer().apply { start() }
        api = Retrofit.Builder().baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().retryOnConnectionFailure(false).build())
            .addConverterFactory(GsonConverterFactory.create()).build().create(AuthApi::class.java)
        store = MemoryTokenStore()
        repository = SessionRepository(api, store)
    }
    @After fun teardown() { server.shutdown() }

    @Test fun `login persists a session and a new repository restores the server profile`() = runBlocking {
        server.enqueue(ok(session)); server.enqueue(ok(profile))
        val user = repository.login("bao@example.com", "A long passphrase for tests")
        assertEquals(12, user.id)
        assertEquals("new-refresh", store.read()!!.refreshToken)
        val restored = SessionRepository(api, store).restore()
        assertEquals(user, restored)
        val login = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("/api/v1/auth/login", login.path)
        assertNull(login.getHeader("Authorization"))
        val me = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("/api/v1/me", me.path); assertEquals("Bearer new-access", me.getHeader("Authorization"))
    }

    @Test fun `concurrent expired requests refresh only once`() = runBlocking {
        store.save(SessionTokens("expired-access", "old-refresh", 0))
        server.enqueue(ok(session)); repeat(2) { server.enqueue(ok(profile)) }
        val results = listOf(async { repository.profile() }, async { repository.profile() }).awaitAll()
        assertEquals(listOf(12, 12), results.map { it.id })
        val requests = (1..3).map { server.takeRequest(2, TimeUnit.SECONDS)!! }
        assertEquals(1, requests.count { it.path == "/api/v1/auth/refresh" })
        assertEquals(2, requests.count { it.getHeader("Authorization") == "Bearer new-access" })
        assertEquals("new-refresh", store.read()!!.refreshToken)
    }

    @Test fun `server rejection refreshes once and retries with the new access token`() = runBlocking {
        stored()
        server.enqueue(MockResponse().setResponseCode(401)); server.enqueue(ok(session)); server.enqueue(ok(profile))
        assertEquals(12, repository.profile().id)
        assertEquals("Bearer old-access", server.takeRequest().getHeader("Authorization"))
        assertEquals("/api/v1/auth/refresh", server.takeRequest().path)
        assertEquals("Bearer new-access", server.takeRequest().getHeader("Authorization"))
    }

    @Test fun `invalid refresh ends the session and does not loop`() = runBlocking {
        stored()
        server.enqueue(MockResponse().setResponseCode(401)); server.enqueue(MockResponse().setResponseCode(401))
        try { repository.profile(); fail("Expected session expiry") } catch (_: SessionExpiredException) { }
        assertNull(store.read()); assertEquals(2, server.requestCount)
    }

    @Test fun `temporary server failure preserves the session for retry`() = runBlocking {
        stored()
        server.enqueue(MockResponse().setResponseCode(503))
        try { repository.profile(); fail("Expected server failure") } catch (error: HttpException) { assertEquals(503, error.code()) }
        assertNotNull(store.read()); assertEquals(1, server.requestCount)
    }

    @Test fun `logout clears storage only after server revocation succeeds`() = runBlocking {
        stored()
        server.enqueue(MockResponse().setResponseCode(503))
        try { repository.logout(); fail("Expected server failure") } catch (_: HttpException) { }
        assertNotNull(store.read())
        server.enqueue(MockResponse().setResponseCode(204)); repository.logout()
        assertNull(store.read())
    }

    @Test fun `profile update sends only editable fields to me`() = runBlocking {
        stored(); server.enqueue(ok(profile))
        repository.update(" New Name ", "0901234567")
        val request = server.takeRequest()
        assertEquals("PATCH", request.method); assertEquals("/api/v1/me", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("New Name")); assertFalse(body.contains("userId")); assertFalse(body.contains("role"))
    }

    @Test fun `OTP durations use the monotonic clock and never expose a preview code`() = runBlocking {
        val gateway = RemoteAuthGateway(repository) { 42_000L }
        server.enqueue(ok("""{"id":"challenge-id","email":"bao@example.com","purpose":"VERIFY_EMAIL","expiresInSeconds":600,"resendInSeconds":60}"""))
        val result = gateway.register("Bao", "bao@example.com", "Long enough test password") as AuthReply.Challenge
        assertEquals(642_000L, result.value.expiresAt); assertEquals(102_000L, result.value.resendAt)
        assertEquals(AuthPurpose.REGISTER, result.value.purpose)
        assertFalse(gateway.isPreview); assertNull(gateway.previewCode("challenge-id"))
    }

    @Test fun `backend unverified wrong code and expired reset errors match existing UI states`() = runBlocking {
        val gateway = RemoteAuthGateway(repository) { 0L }
        server.enqueue(error(403, "EMAIL_UNVERIFIED"))
        assertEquals(AuthReply.Unverified, gateway.login("bao@example.com", "Long enough test password"))
        server.enqueue(error(400, "CODE_WRONG"))
        assertEquals(AuthReply.Error(AuthIssue.CODE_WRONG), gateway.verify("challenge", "000000"))
        server.enqueue(error(410, "CODE_EXPIRED"))
        assertEquals(AuthReply.Error(AuthIssue.CODE_EXPIRED), gateway.resetPassword("expired-grant", "Long enough test password"))
        server.enqueue(error(503, "MAIL_UNAVAILABLE"))
        assertEquals(AuthReply.Error(AuthIssue.MAIL_UNAVAILABLE), gateway.requestCode("bao@example.com", AuthPurpose.REGISTER))
        assertNull(store.read())
    }

    @Test fun `real login success stores session before signaling completion`() = runBlocking {
        server.enqueue(ok(session))
        val gateway = RemoteAuthGateway(repository) { 0L }
        assertEquals(AuthReply.Complete, gateway.login("bao@example.com", "Long enough test password"))
        assertNotNull(store.read())
    }
    private fun stored() = store.save(SessionTokens("old-access", "old-refresh", System.currentTimeMillis() + 900_000))
    private fun ok(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    private fun error(status: Int, code: String) = ok("""{"code":"$code","message":"Test error"}""").setResponseCode(status)
    private class MemoryTokenStore : SessionTokenStore {
        private var value: SessionTokens? = null
        override fun read() = value
        override fun save(value: SessionTokens) { this.value = value }
        override fun clear() { value = null }
    }
}
