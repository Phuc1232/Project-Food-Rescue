// SW-04,07: docs are denied under production/default even with a valid bearer.
package com.foodrescue.backend

import com.foodrescue.backend.support.AuthTestFixtures
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.test.context.ActiveProfiles

abstract class SwaggerUiDeniedProfileTests: AuthTestFixtures() {
    @Test fun `docs and aliases never expose UI but API remains authenticated`() {
        register(); val access=login()["accessToken"].asString()
        val paths=listOf("/docs","/docs/","/openapi.yaml","/docs/assets/swagger-ui.css","/docs/assets/swagger-ui-bundle.js",
            "/docs/assets/swagger-initializer.js","/webjars/swagger-ui/5.34.1/swagger-ui.css","/api-docs/index.html","/static/api-docs/index.html")
        for(path in paths) {
            assertEquals(401,call(path).statusCode(),path)
            val r=call(path,token=access); assertEquals(403,r.statusCode(),path); assertFalse(r.body().contains("SwaggerUIBundle"))
        }
        assertEquals(200,call("/api/v1/accounts/me",token=access).statusCode())
    }
}
@ActiveProfiles(profiles=["prod"],inheritProfiles=false)
class SwaggerUiProductionTests: SwaggerUiDeniedProfileTests()
@ActiveProfiles(profiles=["prod","test"],inheritProfiles=false)
class SwaggerUiMixedTestProfileTests: SwaggerUiDeniedProfileTests()
@ActiveProfiles(profiles=["prod","dev"],inheritProfiles=false)
class SwaggerUiMixedDevProfileTests: SwaggerUiDeniedProfileTests()
@ActiveProfiles(inheritProfiles=false)
class SwaggerUiDefaultProfileTests: SwaggerUiDeniedProfileTests()
