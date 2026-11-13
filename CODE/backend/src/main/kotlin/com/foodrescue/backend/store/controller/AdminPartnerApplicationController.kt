// AC-06,07,08,09,10,11,12: AdminPartnerApplicationController responsibility.
package com.foodrescue.backend.store.controller

import com.foodrescue.backend.common.response.PageResponse
import com.foodrescue.backend.common.security.CurrentAccount
import com.foodrescue.backend.store.ApplicationStatus
import com.foodrescue.backend.store.dto.AdminApplicationDetail
import com.foodrescue.backend.store.dto.AdminApplicationSummary
import com.foodrescue.backend.store.dto.DecisionRequest
import com.foodrescue.backend.store.service.PartnerApplicationReviewService
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/partner-applications")
@PreAuthorize("hasRole('ADMIN')")
class AdminPartnerApplicationController(private val reviews: PartnerApplicationReviewService, private val current: CurrentAccount) {
    @GetMapping
    fun list(@RequestParam(required = false) status: ApplicationStatus?, @RequestParam(defaultValue = "0") page: Int,
             @RequestParam(defaultValue = "20") size: Int): PageResponse<AdminApplicationSummary> = reviews.list(status, page, size)

    @GetMapping("/{id}")
    fun detail(@PathVariable id: UUID): AdminApplicationDetail = reviews.detail(id)

    @PostMapping("/{id}/decision")
    fun decide(@PathVariable id: UUID, @Valid @RequestBody request: DecisionRequest): AdminApplicationDetail =
        reviews.decide(current.get().accountId, id, request)
}
