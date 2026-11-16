// AC-01,02,04 (T6.4): RefundObligationService responsibility.
package com.foodrescue.backend.payment.service

import com.foodrescue.backend.order.entity.CustomerOrder
import com.foodrescue.backend.payment.RefundRequestStatus
import com.foodrescue.backend.payment.entity.Payment
import com.foodrescue.backend.payment.entity.RefundRequest
import com.foodrescue.backend.payment.repository.RefundRequestRepository
import com.foodrescue.backend.store.service.StoreHistoryRecorder
import org.springframework.stereotype.Component

/**
 * Money that cannot be used for its order becomes an obligation to refund the whole received amount (BR-26, UC-13 E3, D3). Called inside the
 * transaction that marks the payment for review; one obligation per payment (unique index), so retries never create a second one.
 */
@Component
class RefundObligationService(private val obligations: RefundRequestRepository, private val history: StoreHistoryRecorder) {
    fun open(payment: Payment, order: CustomerOrder, reasonCode: String, detail: String): RefundRequest? {
        if (obligations.existsByPaymentId(payment.id!!)) return null
        val r = obligations.saveAndFlush(RefundRequest(order, null, "$reasonCode: $detail", payment.receivedAmount, null, RefundRequestStatus.PENDING,
            payment = payment, reasonCode = reasonCode))
        history.record(null, order.store, "REFUND_REQUEST", r.id!!, "REFUND_OBLIGATION_OPENED", null,
            mapOf("paymentId" to payment.id, "amount" to payment.receivedAmount.setScale(0), "reason" to reasonCode))
        return r
    }

    companion object {
        const val LATE_PAYMENT = "LATE_PAYMENT"
        const val ORDER_CANCELLED = "ORDER_CANCELLED"
        const val ALREADY_PAID = "ALREADY_PAID"
        const val AMOUNT_MISMATCH = "AMOUNT_MISMATCH"
        const val ASSIGNED_BY_ADMIN = "ASSIGNED_BY_ADMIN"
    }
}
