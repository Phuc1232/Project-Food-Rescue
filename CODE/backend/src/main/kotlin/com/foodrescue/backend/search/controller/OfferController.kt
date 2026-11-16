// AC-01..05,07 (T5.1): OfferController responsibility.
package com.foodrescue.backend.search.controller

import com.foodrescue.backend.common.response.PageResponse
import com.foodrescue.backend.search.dto.OfferDetail
import com.foodrescue.backend.search.dto.OfferSummary
import com.foodrescue.backend.search.service.OfferQueryService
import org.springframework.core.io.InputStreamResource
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/surprise-boxes")
class OfferController(private val offers: OfferQueryService) {
    @GetMapping
    fun search(@RequestParam(required = false) lat: Double?, @RequestParam(required = false) lng: Double?,
               @RequestParam(required = false) radiusKm: Double?, @RequestParam(required = false) categoryCode: String?,
               @RequestParam(required = false) minPrice: BigDecimal?, @RequestParam(required = false) maxPrice: BigDecimal?,
               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) pickupFrom: Instant?,
               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) pickupTo: Instant?,
               @RequestParam(required = false) sort: String?, @RequestParam(defaultValue = "0") page: Int,
               @RequestParam(defaultValue = "20") size: Int): PageResponse<OfferSummary> =
        offers.search(lat, lng, radiusKm, categoryCode, minPrice, maxPrice, pickupFrom, pickupTo,
            sort ?: if (lat != null && lng != null) "distance" else "pickupStartsAt", page, size)

    @GetMapping("/{batchId}")
    fun detail(@PathVariable batchId: UUID): OfferDetail = offers.detail(batchId)

    @GetMapping("/{batchId}/cover")
    fun cover(@PathVariable batchId: UUID): ResponseEntity<InputStreamResource> {
        val c = offers.cover(batchId)
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(c.mimeType)).contentLength(c.size)
            .header("X-Content-Type-Options", "nosniff").cacheControl(CacheControl.noStore().cachePrivate()).body(InputStreamResource(c.stream))
    }
}
