// AC-02,03,05 (T6.4): AdminPaymentController responsibility.
package com.foodrescue.backend.payment.controller

import com.foodrescue.backend.common.response.PageResponse
import com.foodrescue.backend.common.security.CurrentAccount
import com.foodrescue.backend.payment.PaymentStatus
import com.foodrescue.backend.payment.dto.*
import com.foodrescue.backend.payment.service.AdminPaymentService
import com.foodrescue.backend.payment.service.PaymentReconciliationService
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
class AdminPaymentController(private val payments: AdminPaymentService, private val reconciliation: PaymentReconciliationService, private val current: CurrentAccount) {
    @GetMapping("/payments")
    fun search(@RequestParam(required = false) status: PaymentStatus?, @RequestParam(required = false) orderCode: String?,
               @RequestParam(required = false) reference: String?, @RequestParam(required = false) bucket: String?,
               @RequestParam(defaultValue = "0") page: Int, @RequestParam(defaultValue = "20") size: Int): PageResponse<AdminPaymentView> =
        payments.search(status, orderCode, reference, bucket, page, size)

    @GetMapping("/payments/{paymentId}")
    fun detail(@PathVariable paymentId: UUID): AdminPaymentView = payments.detail(paymentId)

    @PostMapping("/payments/{paymentId}/assign")
    fun assign(@PathVariable paymentId: UUID, @Valid @RequestBody r: PaymentAssignRequest): AdminPaymentView =
        payments.assign(current.get().accountId, paymentId, r.orderCode!!, r.note!!)

    @GetMapping("/orders/{orderId}/money")
    fun orderMoney(@PathVariable orderId: UUID): OrderMoneySummary = payments.orderMoney(orderId)

    @PostMapping("/payment-reconciliation/run")
    fun run(): ReconciliationReport = reconciliation.run()
}
