// SW-01,04,05,08: serve only the local UI assets, with the same profile gate as docs.
package com.foodrescue.backend.config

import com.foodrescue.backend.common.exception.ApiException
import org.springframework.context.annotation.Profile
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
@Profile("(dev | test) & !prod")
class SwaggerUiAssetController {
    @GetMapping("/docs/assets/{filename}")
    fun asset(@PathVariable filename: String): ResponseEntity<Resource> {
        val location = when (filename) {
            "swagger-ui.css", "swagger-ui-bundle.js" -> "api-docs/vendor/$filename"
            "swagger-initializer.js" -> "api-docs/$filename"
            else -> throw ApiException(404, "NOT_FOUND", "Resource not found")
        }
        val type = if (filename.endsWith(".css")) "text/css;charset=UTF-8" else "text/javascript;charset=UTF-8"
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type)).body(ClassPathResource(location))
    }
}
