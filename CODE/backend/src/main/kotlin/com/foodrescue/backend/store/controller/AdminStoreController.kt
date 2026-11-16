// AC-09,10,11,15: AdminStoreController responsibility.
package com.foodrescue.backend.store.controller

import com.foodrescue.backend.common.response.PageResponse
import com.foodrescue.backend.common.security.CurrentAccount
import com.foodrescue.backend.store.StoreStatus
import com.foodrescue.backend.store.dto.AdminStoreDetail
import com.foodrescue.backend.store.dto.AdminStoreSummary
import com.foodrescue.backend.store.dto.StoreStatusRequest
import com.foodrescue.backend.store.service.StoreAdminQueryService
import com.foodrescue.backend.store.service.StoreStatusService
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/stores")
@PreAuthorize("hasRole('ADMIN')")
class AdminStoreController(
    private val queries: StoreAdminQueryService,
    private val statuses: StoreStatusService,
    private val current: CurrentAccount
) {
    @GetMapping
    fun list(@RequestParam(required = false) status: StoreStatus?, @RequestParam(required = false) q: String?,
             @RequestParam(defaultValue = "0") page: Int, @RequestParam(defaultValue = "20") size: Int): PageResponse<AdminStoreSummary> =
        queries.list(status, q, page, size)

    @GetMapping("/{storeId}")
    fun detail(@PathVariable storeId: UUID): AdminStoreDetail = queries.detail(storeId)

    @PostMapping("/{storeId}/status")
    fun changeStatus(@PathVariable storeId: UUID, @Valid @RequestBody request: StoreStatusRequest): AdminStoreDetail =
        statuses.change(current.get().accountId, storeId, request)
}
