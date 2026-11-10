// AC-01..26: RateLimitTests responsibility.
package com.foodrescue.backend

import com.foodrescue.backend.support.AuthTestFixtures
import com.foodrescue.backend.middleware.ratelimit.*
import com.foodrescue.backend.middleware.request.ClientIpResolver
import com.foodrescue.backend.common.exception.ApiException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockHttpServletRequest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
class RateLimitTests: AuthTestFixtures() {
    @Autowired lateinit var store: RateLimitStore
    @Autowired lateinit var properties: RateLimitProperties
    @Test fun `two instances share atomic quota and expiry resets using database fixture`() {
        val first=RateLimitService(store,properties); val second=RateLimitService(store,properties)
        val pool=java.util.concurrent.Executors.newFixedThreadPool(8)
        try {
            val tasks=(1..30).map { index -> pool.submit<Boolean> {
                try { (if(index%2==0) first else second).check(RateLimitPolicy.ACTION_IP,"same"); true }
                catch(e: ApiException) { assertEquals(429,e.status); assertTrue(e.retryAfter!!>0); false }
            } }
            assertEquals(20,tasks.count { it.get() })
            assertEquals(30,jdbc.queryForObject("select count from auth_rate_limit",Int::class.java))
            assertFalse(jdbc.queryForObject("select key_hash from auth_rate_limit",String::class.java)!!.contains("same"))
            jdbc.update("update auth_rate_limit set window_start=window_start-interval '15 minutes',expires_at=expires_at-interval '15 minutes'")
            first.check(RateLimitPolicy.ACTION_IP,"same")
        } finally { pool.shutdownNow() }
    }
    @Test fun `spoof forwarded ip is ignored and trusted chain uses first untrusted hop`() {
        val request=MockHttpServletRequest(); request.remoteAddr="192.0.2.5"; request.addHeader("X-Forwarded-For","1.1.1.1")
        assertEquals("192.0.2.5",ClientIpResolver("").resolve(request))
        request.removeHeader("X-Forwarded-For"); request.addHeader("X-Forwarded-For","6.6.6.6, 198.51.100.2, 10.0.0.2"); request.remoteAddr="10.0.0.1"
        assertEquals("198.51.100.2",ClientIpResolver("10.0.0.0/8").resolve(request))
    }
    @Test fun `http limit returns 429 retry after and request id`() {
        repeat(10) { assertEquals(202,call("/api/v1/auth/forgot-password","POST",mapOf("email" to "missing"+it+"@example.test")).statusCode()) }
        val r=call("/api/v1/auth/forgot-password","POST",mapOf("email" to "another@example.test"))
        assertEquals(429,r.statusCode()); assertTrue(r.headers().firstValue("Retry-After").isPresent)
        assertEquals(json(r)["requestId"].asString(),r.headers().firstValue("X-Request-ID").get())
    }
}
