// AC-01..12: FileController responsibility.
package com.foodrescue.backend.file.controller

import com.foodrescue.backend.common.security.CurrentAccount
import com.foodrescue.backend.file.dto.FileResponse
import com.foodrescue.backend.file.service.FileService
import org.springframework.core.io.InputStreamResource
import org.springframework.http.CacheControl
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.nio.charset.StandardCharsets
import java.util.UUID

@RestController
@RequestMapping("/api/v1/files")
class FileController(private val files: FileService, private val current: CurrentAccount) {
    @PostMapping
    fun upload(@RequestParam("file", required = false) file: MultipartFile?): ResponseEntity<FileResponse> {
        val saved = files.upload(current.get().accountId, file)
        val location = ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}").buildAndExpand(saved.id).toUri()
        return ResponseEntity.created(location).body(saved)
    }

    @GetMapping("/{id}")
    fun metadata(@PathVariable id: UUID): FileResponse = files.metadata(current.get().accountId, id)

    @GetMapping("/{id}/content")
    fun content(@PathVariable id: UUID): ResponseEntity<InputStreamResource> {
        val file = files.content(current.get().accountId, id)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.metadata.mimeType))
            .contentLength(file.metadata.sizeBytes)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(file.metadata.originalName, StandardCharsets.UTF_8).build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(InputStreamResource(file.stream))
    }
}
