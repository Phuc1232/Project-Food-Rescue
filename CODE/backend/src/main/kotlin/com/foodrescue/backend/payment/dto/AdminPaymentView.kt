// AC-02,04,05 (T6.4): AdminPaymentView responsibility.
package com.foodrescue.backend.payment.dto

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** bucket: APPLIED | REFUND_PENDING | REFUNDED | UNASSIGNED | RECEIVED (every received amount is in exactly one). */
data class AdminPaymentView(
    val id: UUID, val provider: String, val providerTransactionId: String, val providerOrderCode: Long?, val orderId: UUID?, val orderCode: String?,
    val amount: BigDecimal, val currency: String, val status: String, val bucket: String, val receivedAt: Instant, val verifiedAt: Instant?,
    val refundRequestId: UUID?, val refundReason: String?
)
