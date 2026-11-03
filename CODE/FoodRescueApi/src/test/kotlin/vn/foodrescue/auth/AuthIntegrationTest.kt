package vn.foodrescue.auth

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mail.MailSendException
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import vn.foodrescue.service.VerificationMailSender
import java.util.concurrent.*

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class AuthIntegrationTest @Autowired constructor(private val mvc: MockMvc, private val json: ObjectMapper, private val jdbc: JdbcTemplate) {
    @MockitoBean lateinit var mail: VerificationMailSender
    private val codes = ConcurrentHashMap<String, String>()
    private val password = "BanhMi24" // Eight-character boundary exercised through real API flows.

    @BeforeEach fun setup() {
        check(jdbc.queryForObject("SELECT current_database()", String::class.java)!!.endsWith("_test")) {
            "Authentication integration tests require an isolated database named *_test"
        }
        jdbc.execute("TRUNCATE tai_khoan CASCADE")
        jdbc.execute("TRUNCATE ma_xac_minh, quyen_dat_lai_mat_khau, gioi_han_xac_thuc")
        codes.clear()
        reset(mail)
        doAnswer { invocation -> codes[invocation.getArgument(0)] = invocation.getArgument(1); null }
            .`when`(mail).send(anyString(), anyString(), anyString())
    }

    @Test fun `registration verifies ownership and never stores a plaintext password or OTP`() {
        expect(postJson("/auth/register", mapOf("name" to "Test Customer", "email" to "Bao@Example.com", "password" to "BanhMi2")), 400, "VALIDATION_ERROR")
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tai_khoan", Int::class.java))
        verifyNoInteractions(mail)
        val challenge = register("Bao@Example.com")
        assertEquals("bao@example.com", challenge["email"].asText())
        assertFalse(challenge.has("code"))
        val hash = jdbc.queryForObject("SELECT mat_khau_hash FROM tai_khoan", String::class.java)!!
        assertTrue(hash.startsWith("\$argon2id\$")); assertNotEquals(password, hash)
        assertNotEquals(codes["bao@example.com"], jdbc.queryForObject("SELECT ma_hash FROM ma_xac_minh", String::class.java))
        assertEquals("KHACH_HANG", jdbc.queryForObject("SELECT vai_tro FROM tai_khoan", String::class.java))
        expect(login("bao@example.com"), 403, "EMAIL_UNVERIFIED")
        expect(postJson("/auth/register", mapOf("name" to "Another User", "email" to "BAO@example.com", "password" to password)), 409, "EMAIL_USED")
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM tai_khoan", Int::class.java))
        expect(verify(challenge, codes["bao@example.com"]!!), 200)
        expect(verify(challenge, codes["bao@example.com"]!!), 410, "CODE_EXPIRED")
        expect(login("bao@example.com"), 200)
    }

    @Test fun `profile reads and writes only the signed in customer and survives a new login`() {
        active("a@example.com"); active("b@example.com")
        val a = body(expect(login("a@example.com"), 200)); val b = body(expect(login("b@example.com"), 200))
        val access = a["accessToken"].asText()
        val edit = mvc.perform(patch("/api/v1/me").header("Authorization", "Bearer $access").contentType("application/json")
            .content(json.writeValueAsString(mapOf("name" to "Nguyễn Gia Bảo", "phone" to "0901234567")))).andReturn()
        expect(edit, 200)
        assertEquals("Nguyễn Gia Bảo", body(me(access))["name"].asText())
        assertEquals("Test Customer", body(me(b["accessToken"].asText()))["name"].asText())
        assertEquals("0901234567", body(expect(login("a@example.com"), 200))["user"]["phone"].asText())
        val inject = mvc.perform(patch("/api/v1/me").header("Authorization", "Bearer $access").contentType("application/json")
            .content("{\"name\":\"Other User\",\"phone\":\"\",\"id\":${b["user"]["id"]},\"role\":\"QUAN_TRI_VIEN\"}")).andReturn()
        expect(inject, 400)
        expect(mvc.perform(get("/api/v1/users/${b["user"]["id"]}").header("Authorization", "Bearer $access")).andReturn(), 403)
        assertEquals("a@example.com", body(mvc.perform(get("/api/v1/me?userId=${b["user"]["id"]}")
            .header("Authorization", "Bearer $access")).andReturn())["email"].asText())
    }

    @Test fun `five wrong OTP attempts persist and lock the challenge`() {
        val c = register(); val actual = codes["test@example.com"]!!; val wrong = wrong(actual)
        repeat(5) { expect(verify(c, wrong), 400, "CODE_WRONG") }
        assertEquals(5, jdbc.queryForObject("SELECT so_lan_sai FROM ma_xac_minh", Int::class.java))
        expect(verify(c, actual), 429, "TOO_MANY_ATTEMPTS")
    }

    @Test fun `resend enforces cooldown and invalidates the old code`() {
        val c = register(); val old = codes["test@example.com"]!!
        expect(postJson("/auth/challenges/${c["id"].asText()}/resend"), 429, "COOLDOWN")
        jdbc.update("UPDATE ma_xac_minh SET gui_lai_luc=now()-interval '1 second'")
        expect(postJson("/auth/challenges/${c["id"].asText()}/resend"), 200)
        assertNotEquals(old, codes["test@example.com"])
        expect(verify(c, old), 400, "CODE_WRONG")
        expect(verify(c, codes["test@example.com"]!!), 200)
    }

    @Test fun `expired codes cannot verify and new challenge invalidates an earlier one`() {
        val c = register()
        jdbc.execute("UPDATE ma_xac_minh SET tao_luc=now()-interval '2 hours',het_han_luc=now()-interval '1 hour',gui_lai_luc=now()-interval '1 hour'")
        expect(verify(c, codes["test@example.com"]!!), 410)
        val newer = body(expect(postJson("/auth/email-verifications", mapOf("email" to "test@example.com")), 200))
        expect(verify(c, codes["test@example.com"]!!), 410)
        expect(verify(newer, codes["test@example.com"]!!), 200)
    }

    @Test fun `reset grants are scoped single use and revoke every existing session`() {
        active(); val first = body(expect(login(), 200)); val second = body(expect(login(), 200))
        val c = body(expect(postJson("/auth/password-reset-requests", mapOf("email" to "test@example.com")), 200))
        val grant = body(expect(verify(c, codes["test@example.com"]!!), 200))["resetGrant"].asText()
        val replacement = "CaPhe246"
        expect(postJson("/auth/password-resets", mapOf("resetGrant" to grant, "password" to "CaPhe24")), 400, "VALIDATION_ERROR")
        expect(login(), 200)
        expect(postJson("/auth/password-resets", mapOf("resetGrant" to "guessed", "password" to replacement)), 410)
        expect(postJson("/auth/password-resets", mapOf("resetGrant" to grant, "password" to replacement)), 204)
        expect(postJson("/auth/password-resets", mapOf("resetGrant" to grant, "password" to replacement)), 410)
        expect(me(first["accessToken"].asText()), 401); expect(me(second["accessToken"].asText()), 401)
        expect(postJson("/auth/refresh", mapOf("refreshToken" to first["refreshToken"].asText())), 401)
        expect(login(), 401, "CREDENTIALS")
        expect(login(password = replacement), 200)
    }

    @Test fun `email verification does not issue a password reset grant`() {
        val c = register()
        val result = body(expect(verify(c, codes["test@example.com"]!!), 200))
        assertTrue(result["resetGrant"].isNull)
    }

    @Test fun `expired reset grant is rejected`() {
        active()
        val c = body(expect(postJson("/auth/password-reset-requests", mapOf("email" to "test@example.com")), 200))
        val grant = body(expect(verify(c, codes["test@example.com"]!!), 200))["resetGrant"].asText()
        jdbc.execute("UPDATE quyen_dat_lai_mat_khau SET tao_luc=now()-interval '2 hours',het_han_luc=now()-interval '1 hour'")
        expect(postJson("/auth/password-resets", mapOf("resetGrant" to grant, "password" to "A replacement long password")), 410)
    }

    @Test fun `unknown reset email receives the same response shape and no email`() {
        val c = body(expect(postJson("/auth/password-reset-requests", mapOf("email" to "nobody@example.com")), 200))
        assertTrue(c.has("id") && c.has("expiresInSeconds") && c.has("resendInSeconds"))
        assertTrue(codes.isEmpty())
        expect(verify(c, "123456"), 400, "CODE_WRONG")
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM quyen_dat_lai_mat_khau", Int::class.java))
    }

    @Test fun `rotation invalidates the old access token and replay revokes the token family`() {
        active(); val old = body(expect(login(), 200))
        val next = body(expect(postJson("/auth/refresh", mapOf("refreshToken" to old["refreshToken"].asText())), 200))
        assertNotEquals(old["refreshToken"], next["refreshToken"])
        expect(me(old["accessToken"].asText()), 401); expect(me(next["accessToken"].asText()), 200)
        expect(postJson("/auth/refresh", mapOf("refreshToken" to old["refreshToken"].asText())), 401)
        expect(me(next["accessToken"].asText()), 401)
    }

    @Test fun `logout revokes this device without logging out another device`() {
        active(); val first = body(expect(login(), 200)); val second = body(expect(login(), 200))
        expect(mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer ${first["accessToken"].asText()}")).andReturn(), 204)
        expect(me(first["accessToken"].asText()), 401)
        expect(me(second["accessToken"].asText()), 200)
        expect(postJson("/auth/refresh", mapOf("refreshToken" to first["refreshToken"].asText())), 401)
    }

    @Test fun `account lock immediately blocks a previously valid access token`() {
        active(); val s = body(expect(login(), 200))
        jdbc.update("UPDATE tai_khoan SET trang_thai='BI_KHOA'")
        expect(me(s["accessToken"].asText()), 401)
        expect(login(), 401)
        expect(postJson("/auth/refresh", mapOf("refreshToken" to s["refreshToken"].asText())), 401)
    }

    @Test fun `expired sessions and malformed or tampered JWTs cannot access a profile`() {
        active(); val s = body(expect(login(), 200))
        expect(me("not-a-jwt"), 401)
        expect(me(s["accessToken"].asText().dropLast(8) + "tampered"), 401)
        jdbc.execute("UPDATE phien_dang_nhap SET tao_luc=now()-interval '2 hours',het_han_luc=now()-interval '1 hour'")
        expect(me(s["accessToken"].asText()), 401)
        expect(postJson("/auth/refresh", mapOf("refreshToken" to s["refreshToken"].asText())), 401)
    }

    @Test fun `rate limits survive failed transactions and forged forwarded headers`() {
        repeat(10) { expect(login("missing@example.com"), 401) }
        val result = mvc.perform(post("/api/v1/auth/login").header("X-Forwarded-For", "1.2.3.4")
            .contentType("application/json").content(json.writeValueAsString(mapOf("email" to "missing@example.com", "password" to password)))).andReturn()
        expect(result, 429, "TOO_MANY_ATTEMPTS")
        assertEquals("600", result.response.getHeader("Retry-After"))
    }

    @Test fun `public registration rejects role injection malformed bodies and short passwords`() {
        expect(postJson("/auth/register", mapOf("name" to "Bao", "email" to "b@example.com", "password" to "short")), 400)
        expect(postJson("/auth/register", mapOf("name" to "Bao", "email" to "b@example.com", "password" to password, "role" to "QUAN_TRI_VIEN")), 400)
        expect(postJson("/auth/register", mapOf("name" to "  ", "email" to "b@example.com", "password" to password)), 400)
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tai_khoan", Int::class.java))
    }

    @Test fun `long unicode passwords are not silently truncated`() {
        val long = "Chào bạn 🌿 FoodRescue ".repeat(4)
        val c = body(expect(postJson("/auth/register", mapOf("name" to "Bảo", "email" to "unicode@example.com", "password" to long)), 201))
        expect(verify(c, codes["unicode@example.com"]!!), 200)
        expect(login("unicode@example.com", long), 200)
        expect(login("unicode@example.com", long.dropLast(1) + "x"), 401)
    }

    @Test fun `mail failure rolls back account and challenge and is reported honestly`() {
        doThrow(MailSendException("test mail failure")).`when`(mail).send(anyString(), anyString(), anyString())
        expect(postJson("/auth/register", mapOf("name" to "Bao", "email" to "b@example.com", "password" to password)), 503, "MAIL_UNAVAILABLE")
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tai_khoan", Int::class.java))
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM ma_xac_minh", Int::class.java))
    }

    @Test fun `simultaneous duplicate registrations create only one account`() {
        val results = parallel { postJson("/auth/register", mapOf("name" to "Customer", "email" to "same@example.com", "password" to password)) }
        assertEquals(listOf(201, 409), results.map { it.response.status }.sorted())
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM tai_khoan", Int::class.java))
    }

    @Test fun `simultaneous verifications consume a challenge only once`() {
        val c = register(); val code = codes["test@example.com"]!!
        assertEquals(listOf(200, 410), parallel { verify(c, code) }.map { it.response.status }.sorted())
    }

    @Test fun `simultaneous refreshes cannot produce two live replacement sessions`() {
        active(); val s = body(expect(login(), 200))
        val results = parallel { postJson("/auth/refresh", mapOf("refreshToken" to s["refreshToken"].asText())) }
        assertEquals(listOf(200, 401), results.map { it.response.status }.sorted())
        val replacement = body(results.single { it.response.status == 200 })
        expect(me(replacement["accessToken"].asText()), 401)
    }

    private fun parallel(block: () -> MvcResult): List<MvcResult> {
        val pool = Executors.newFixedThreadPool(2); val gate = CountDownLatch(1)
        return try {
            val jobs = (1..2).map { pool.submit<MvcResult> { gate.await(); block() } }; gate.countDown()
            jobs.map { it.get(20, TimeUnit.SECONDS) }
        } finally { pool.shutdownNow() }
    }
    private fun register(email: String = "test@example.com") = body(expect(postJson("/auth/register",
        mapOf("name" to "Test Customer", "email" to email, "password" to password)), 201))
    private fun active(email: String = "test@example.com") { val c = register(email); expect(verify(c, codes[email]!!), 200) }
    private fun verify(challenge: JsonNode, code: String) = postJson("/auth/challenges/${challenge["id"].asText()}/verify", mapOf("code" to code))
    private fun login(email: String = "test@example.com", password: String = this.password) = postJson("/auth/login", mapOf("email" to email, "password" to password))
    private fun me(access: String) = mvc.perform(get("/api/v1/me").header("Authorization", "Bearer $access")).andReturn()
    private fun postJson(path: String, value: Any = emptyMap<String, String>()): MvcResult = mvc.perform(post("/api/v1$path")
        .contentType("application/json").content(json.writeValueAsString(value))).andReturn()
    private fun body(result: MvcResult): JsonNode = json.readTree(result.response.contentAsString)
    private fun expect(result: MvcResult, status: Int, code: String? = null): MvcResult {
        assertEquals(status, result.response.status)
        if (code != null) assertEquals(code, body(result)["code"].asText())
        return result
    }
    private fun wrong(code: String) = if (code == "000000") "999999" else "000000"
}
