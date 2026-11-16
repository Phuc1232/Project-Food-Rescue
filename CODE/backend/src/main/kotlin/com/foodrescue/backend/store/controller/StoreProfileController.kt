// AC-01,02,03,04,05,06,08,13: StoreProfileController responsibility.
package com.foodrescue.backend.store.controller

import com.foodrescue.backend.common.response.PageResponse
import com.foodrescue.backend.common.security.CurrentAccount
import com.foodrescue.backend.store.dto.ChangeRequestView
import com.foodrescue.backend.store.dto.StoreChangeRequestRequest
import com.foodrescue.backend.store.dto.StoreHistoryItem
import com.foodrescue.backend.store.dto.StoreProfileView
import com.foodrescue.backend.store.service.StoreChangeRequestService
import com.foodrescue.backend.store.service.StoreProfileService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.UUID

/** Owner management of the store (UC-40, UC-41). */
@RestController
@RequestMapping("/api/v1/stores/{storeId}")
class StoreProfileController(
    private val profiles: StoreProfileService,
    private val changes: StoreChangeRequestService,
    private val current: CurrentAccount
) {
    @GetMapping("/profile")
    fun profile(@PathVariable storeId: UUID): StoreProfileView = profiles.view(current.get().accountId, storeId)

    @PatchMapping("/profile")
    fun update(@PathVariable storeId: UUID, @RequestBody(required = false) body: Map<String, Any?>?): StoreProfileView =
        profiles.update(current.get().accountId, storeId, body)

    @PostMapping("/change-requests")
    @ResponseStatus(HttpStatus.CREATED)
    fun requestChange(@PathVariable storeId: UUID, @Valid @RequestBody request: StoreChangeRequestRequest): ChangeRequestView =
        changes.submit(current.get().accountId, storeId, request)

    @GetMapping("/change-requests")
    fun changeRequests(@PathVariable storeId: UUID): List<ChangeRequestView> = changes.list(current.get().accountId, storeId)

    @PostMapping("/change-requests/{requestId}/cancel")
    fun cancel(@PathVariable storeId: UUID, @PathVariable requestId: UUID): ChangeRequestView =
        changes.cancel(current.get().accountId, storeId, requestId)

    @GetMapping("/history")
    fun history(@PathVariable storeId: UUID, @RequestParam(defaultValue = "0") page: Int,
                @RequestParam(defaultValue = "20") size: Int): PageResponse<StoreHistoryItem> =
        profiles.history(current.get().accountId, storeId, page, size)
}
