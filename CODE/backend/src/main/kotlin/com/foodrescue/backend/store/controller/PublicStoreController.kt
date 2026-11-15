// AC-12: PublicStoreController responsibility.
package com.foodrescue.backend.store.controller

import com.foodrescue.backend.store.dto.StorePublicView
import com.foodrescue.backend.store.service.StoreProfileService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Customer view of a store (any signed-in account, D10); only ACTIVE stores are visible. */
@RestController
class PublicStoreController(private val profiles: StoreProfileService) {
    @GetMapping("/api/v1/stores/{storeId}")
    fun store(@PathVariable storeId: UUID): StorePublicView = profiles.publicView(storeId)
}
