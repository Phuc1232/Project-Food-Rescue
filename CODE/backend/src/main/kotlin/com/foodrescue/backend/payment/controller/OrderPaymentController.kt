// AC-01..06 (T6.2): OrderPaymentController responsibility.
package com.foodrescue.backend.payment.controller

import com.foodrescue.backend.common.security.CurrentAccount
import com.foodrescue.backend.payment.dto.PaymentRequestView
import com.foodrescue.backend.payment.service.PaymentRequestService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/orders/{orderId}/payment")
class OrderPaymentController(private val payments: PaymentRequestService, private val current: CurrentAccount) {
    /** 201 when a link was created now, 200 when the live one is returned (UC-15 A1). */
    @PostMapping
    fun getOrCreate(@PathVariable orderId: UUID): ResponseEntity<PaymentRequestView> {
        val r = payments.getOrCreate(current.get().accountId, orderId)
        return ResponseEntity.status(if (r.created) HttpStatus.CREATED else HttpStatus.OK).body(r.view)
    }

    @GetMapping
    fun current(@PathVariable orderId: UUID): PaymentRequestView = payments.current(current.get().accountId, orderId)
}
