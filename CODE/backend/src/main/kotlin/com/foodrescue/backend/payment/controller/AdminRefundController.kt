// AC-01..05 (T6.5): AdminRefundController responsibility.
package com.foodrescue.backend.payment.controller

import com.foodrescue.backend.common.response.PageResponse
import com.foodrescue.backend.common.security.CurrentAccount
import com.foodrescue.backend.payment.RefundRequestStatus
import com.foodrescue.backend.payment.dto.*
import com.foodrescue.backend.payment.service.ManualRefundService
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/refund-requests")
@PreAuthorize("hasRole('ADMIN')")
class AdminRefundController(private val refunds: ManualRefundService, private val current: CurrentAccount) {
    @GetMapping
    fun list(@RequestParam(required = false) status: RefundRequestStatus?, @RequestParam(defaultValue = "0") page: Int,
             @RequestParam(defaultValue = "20") size: Int): PageResponse<RefundRequestView> = refunds.list(status, page, size)

    @GetMapping("/{id}")
    fun detail(@PathVariable id: UUID): RefundRequestView = refunds.detail(id)

    @PostMapping("/{id}/approve")
    fun approve(@PathVariable id: UUID, @Valid @RequestBody r: RefundApproveRequest): RefundRequestView = refunds.approve(current.get().accountId, id, r)

    @PostMapping("/{id}/confirm")
    fun confirm(@PathVariable id: UUID, @Valid @RequestBody r: RefundConfirmRequest): RefundRequestView = refunds.confirm(current.get().accountId, id, r)

    @PostMapping("/{id}/fail")
    fun fail(@PathVariable id: UUID, @Valid @RequestBody r: RefundFailRequest): RefundRequestView = refunds.fail(current.get().accountId, id, r.note!!)
}
