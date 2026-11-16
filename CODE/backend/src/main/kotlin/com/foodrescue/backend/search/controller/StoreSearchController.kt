// AC-06 (T5.1): StoreSearchController responsibility.
package com.foodrescue.backend.search.controller

import com.foodrescue.backend.common.response.PageResponse
import com.foodrescue.backend.search.dto.StoreCard
import com.foodrescue.backend.search.dto.StorePin
import com.foodrescue.backend.search.service.StoreSearchService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class StoreSearchController(private val stores: StoreSearchService) {
    @GetMapping("/api/v1/store-search")
    fun search(@RequestParam(required = false) q: String?, @RequestParam(required = false) lat: Double?, @RequestParam(required = false) lng: Double?,
               @RequestParam(required = false) radiusKm: Double?, @RequestParam(defaultValue = "0") page: Int,
               @RequestParam(defaultValue = "20") size: Int): PageResponse<StoreCard> = stores.search(q, lat, lng, radiusKm, page, size)

    @GetMapping("/api/v1/store-map")
    fun map(@RequestParam minLat: Double, @RequestParam maxLat: Double, @RequestParam minLng: Double, @RequestParam maxLng: Double): List<StorePin> =
        stores.map(minLat, maxLat, minLng, maxLng)
}
