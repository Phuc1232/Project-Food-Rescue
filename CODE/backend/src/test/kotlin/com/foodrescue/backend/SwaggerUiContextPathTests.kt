// SW-01..06: context path regression and opt-in browser sandbox using only test DB/mail.
package com.foodrescue.backend

import com.foodrescue.backend.support.AuthTestFixtures
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties=["server.servlet.context-path=/foodrescue"])
@ActiveProfiles(profiles=["dev"],inheritProfiles=false)
class SwaggerUiContextPathTests: AuthTestFixtures() {
    @Test fun `redirect spec and API stay under the servlet context path`() {
        assertEquals("/foodrescue",contextPath)
        val redirect=call("/docs"); assertEquals(302,redirect.statusCode()); assertEquals("docs/",redirect.headers().firstValue("Location").get())
        assertEquals(200,call("/docs/").statusCode()); assertEquals(200,call("/docs/assets/swagger-ui-bundle.js").statusCode())
        assertEquals(".",json(call("/openapi.yaml"))["servers"][0]["url"].asString())
        register(); assertEquals(200,call("/api/v1/accounts/me",token=login()["accessToken"].asString()).statusCode())
    }
    @Test fun `quota and request size limits still apply below context prefix`() {
        assertEquals(413,call("/api/v1/auth/register","POST","x".repeat(17000)).statusCode())
        repeat(10) { assertEquals(202,call("/api/v1/auth/forgot-password","POST",mapOf("email" to "quota$it@example.test")).statusCode()) }
        val limited=call("/api/v1/auth/forgot-password","POST",mapOf("email" to "another@example.test"))
        assertEquals(429,limited.statusCode()); assertTrue(limited.headers().firstValue("Retry-After").isPresent)
    }
    // Enable only for a local browser run; no real credentials, Compose DB or SMTP are used.
    @Test @EnabledIfEnvironmentVariable(named="SWAGGER_BROWSER_SMOKE",matches="true")
    fun `hold disposable browser sandbox until stop signal`() {
        register("developer@example.test")
        val dir=Path.of("tmp"); Files.createDirectories(dir)
        val stop=dir.resolve("swagger-browser-stop"); Files.deleteIfExists(stop)
        val info=dir.resolve("swagger-browser-sandbox.json")
        Files.writeString(info,mapper.writeValueAsString(mapOf("url" to "http://localhost:$port$contextPath/docs/","email" to "developer@example.test","password" to password)))
        try {
            val deadline=Instant.now().plusSeconds(1200)
            while(!Files.exists(stop) && Instant.now().isBefore(deadline)) Thread.sleep(500)
            check(Files.exists(stop)) { "Browser sandbox timed out" }
        } finally { Files.deleteIfExists(info); Files.deleteIfExists(stop) }
    }
}
