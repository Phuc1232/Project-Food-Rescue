// AC-09: ProfileUpdateCorsTests responsibility.
package com.foodrescue.backend

import com.foodrescue.backend.support.AuthTestFixtures
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@TestPropertySource(properties = ["auth.cors-origins=https://web.example.test"])
class ProfileUpdateCorsTests : AuthTestFixtures() {
    private fun preflight(origin: String): HttpResponse<String> = HttpClient.newHttpClient().send(
        HttpRequest.newBuilder(URI("http://localhost:$port$contextPath/api/v1/accounts/me"))
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
            .header("Origin", origin).header("Access-Control-Request-Method", "PATCH")
            .header("Access-Control-Request-Headers", "authorization,content-type").build(),
        HttpResponse.BodyHandlers.ofString())

    @Test fun `allowed origin may preflight PATCH on the profile`() {
        val r = preflight("https://web.example.test")
        assertEquals(200, r.statusCode())
        assertTrue(r.headers().firstValue("Access-Control-Allow-Methods").orElse("").contains("PATCH"))
        assertEquals(403, preflight("https://evil.example.test").statusCode())
    }
}
