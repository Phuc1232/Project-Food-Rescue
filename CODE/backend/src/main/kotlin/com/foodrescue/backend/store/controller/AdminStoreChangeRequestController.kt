// AC-07: AdminStoreChangeRequestController responsibility.
package com.foodrescue.backend.store.controller

import com.foodrescue.backend.common.response.PageResponse
import com.foodrescue.backend.common.security.CurrentAccount
import com.foodrescue.backend.store.StoreChangeRequestStatus
import com.foodrescue.backend.store.dto.AdminChangeRequestView
import com.foodrescue.backend.store.dto.ChangeDecisionRequest
import com.foodrescue.backend.store.service.StoreChangeReviewService
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/store-change-requests")
@PreAuthorize("hasRole('ADMIN')")
class AdminStoreChangeRequestController(private val reviews: StoreChangeReviewService, private val current: CurrentAccount) {
    @GetMapping
    fun list(@RequestParam(required = false) status: StoreChangeRequestStatus?, @RequestParam(defaultValue = "0") page: Int,
             @RequestParam(defaultValue = "20") size: Int): PageResponse<AdminChangeRequestView> = reviews.list(status, page, size)

    @GetMapping("/{id}")
    fun detail(@PathVariable id: UUID): AdminChangeRequestView = reviews.detail(id)

    @PostMapping("/{id}/decision")
    fun decide(@PathVariable id: UUID, @Valid @RequestBody request: ChangeDecisionRequest): AdminChangeRequestView =
        reviews.decide(current.get().accountId, id, request)
}
