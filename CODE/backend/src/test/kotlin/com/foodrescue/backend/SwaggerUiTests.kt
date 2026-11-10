// SW-01,02,05,07,08: HTTP resource contract and local Swagger UI configuration.
package com.foodrescue.backend

import com.foodrescue.backend.support.AuthTestFixtures
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.test.context.ActiveProfiles

open class SwaggerUiTests: AuthTestFixtures() {
    @Test fun `docs redirects relatively and all required local assets load`() {
        val redirect=call("/docs")
        assertEquals(302,redirect.statusCode()); assertEquals("docs/",redirect.headers().firstValue("Location").get())
        val html=call("/docs/"); assertEquals(200,html.statusCode()); assertTrue(html.body().contains("id=\"swagger-ui\""))
        assertTrue(html.headers().firstValue("Content-Type").get().startsWith("text/html"))
        for((name,type) in listOf("swagger-ui.css" to "text/css","swagger-ui-bundle.js" to "text/javascript","swagger-initializer.js" to "text/javascript")) {
            val r=call("/docs/assets/"+name); assertEquals(200,r.statusCode(),name)
            assertTrue(r.headers().firstValue("Content-Type").get().startsWith(type)); assertTrue(r.body().isNotBlank())
            assertTrue(r.headers().firstValue("X-Request-ID").isPresent)
        }
        assertEquals(404,call("/docs/assets/unknown.js").statusCode())
        assertEquals(401,call("/api/v1/accounts/me").statusCode())
        assertEquals(401,call("/docs/","POST").statusCode())
    }
    @Test fun `UI uses static reviewed contract with safe configuration and examples`() {
        val contract=json(call("/openapi.yaml")); assertEquals(15,contract["paths"].size())
        assertEquals(".",contract["servers"][0]["url"].asString())
        assertEquals("bearer",contract["components"]["securitySchemes"]["bearerAuth"]["scheme"].asString())
        assertEquals(6,contract["tags"].size())
        val js=call("/docs/assets/swagger-initializer.js").body()
        for(value in listOf("persistAuthorization: false","queryConfigEnabled: false","validatorUrl: null","withCredentials: false","../openapi.yaml")) assertTrue(js.contains(value),value)
        assertFalse(js.contains("localStorage")); assertFalse(js.contains("sessionStorage"))
        val loader=javaClass.classLoader
        assertNotNull(loader.getResource("api-docs/vendor/swagger-ui-bundle.js.LICENSE.txt"))
        assertNotNull(loader.getResource("api-docs/LICENSE.swagger-ui.txt"))
        assertNull(loader.getResource("META-INF/resources/webjars/swagger-ui/5.34.1/index.html"))
    }
}

@ActiveProfiles(profiles=["dev"],inheritProfiles=false)
class SwaggerUiDevTests: SwaggerUiTests()
