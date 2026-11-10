// AC-01..26: RefreshTokenConcurrencyTests responsibility.
package com.foodrescue.backend

import com.foodrescue.backend.support.AuthTestFixtures
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
class RefreshTokenConcurrencyTests: AuthTestFixtures() {
    @Test fun `reuse commits revocation before 401 response`() {
        register(); val old=login(); val rotated=refresh(old["refreshToken"].asString()); assertEquals(200,rotated.statusCode())
        assertEquals(401,refresh(old["refreshToken"].asString()).statusCode())
        assertEquals(401,call("/api/v1/accounts/me",token=json(rotated)["accessToken"].asString()).statusCode())
        assertEquals(2,jdbc.queryForObject("select count(*) from refresh_token",Int::class.java))
        assertEquals(1,jdbc.queryForObject("select count(*) from login_session where revoked_at is not null",Int::class.java))
    }
    @Test fun `concurrent refresh rotates once and reuse revokes winning token`() {
        register(); val old=login()["refreshToken"].asString(); val start=java.util.concurrent.CountDownLatch(1)
        val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val tasks=(1..2).map { pool.submit<java.net.http.HttpResponse<String>> { start.await(); refresh(old) } }
            start.countDown(); val results=tasks.map { it.get(30,java.util.concurrent.TimeUnit.SECONDS) }
            assertEquals(listOf(200,401),results.map { it.statusCode() }.sorted())
            val winner=json(results.first { it.statusCode()==200 })
            assertEquals(401,call("/api/v1/accounts/me",token=winner["accessToken"].asString()).statusCode())
        } finally { pool.shutdownNow() }
    }

    @Test fun `logout all revokes both devices and cannot revoke another accounts session`() {
        register(); val a=login(); val b=login()
        register("other@example.test"); val other=login("other@example.test")
        val id=jdbc.queryForObject("select s.id from login_session s join account a on a.id=s.account_id where a.login_identifier='other@example.test'",java.util.UUID::class.java)!!
        assertEquals(404,call("/api/v1/auth/sessions/"+id,"DELETE",token=a["accessToken"].asString()).statusCode())
        assertEquals(204,call("/api/v1/auth/logout-all","POST",token=a["accessToken"].asString()).statusCode())
        for(pair in listOf(a,b)) assertEquals(401,call("/api/v1/accounts/me",token=pair["accessToken"].asString()).statusCode())
        assertEquals(200,call("/api/v1/accounts/me",token=other["accessToken"].asString()).statusCode())
    }
}
