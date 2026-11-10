// SW-01,04,06: profile-gated Swagger page and the reviewed static contract.
package com.foodrescue.backend.config

import org.springframework.context.annotation.Profile
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@Profile("(dev | test) & !prod")
class ApiDocumentationConfiguration {
    @GetMapping("/openapi.yaml")
    fun contract(): ResponseEntity<Resource> = ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("application/yaml"))
        .body(ClassPathResource("api/openapi.yaml"))

    @GetMapping("/docs")
    fun redirect(): ResponseEntity<Void> = ResponseEntity.status(302).location(URI("docs/")).build()

    @GetMapping("/docs/")
    fun docs(): ResponseEntity<Resource> = ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
        .body(ClassPathResource("api-docs/index.html"))
}
