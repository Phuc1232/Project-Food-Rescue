// AC-01..06 (T6.5): RefundRequestView responsibility.
package com.foodrescue.backend.payment.dto

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * A refund obligation with its source payment. refundableFromPayment = payment amount - refunds already succeeded from it (UC-72 E1).
 * customerEmail / customerName are filled for administrators only (to make the transfer); null for the customer's own view.
 */
data class RefundRequestView(
    val id: UUID, val orderId: UUID, val orderCode: String, val status: String, val reasonCode: String?, val reason: String,
    val requestedAmount: BigDecimal, val approvedAmount: BigDecimal?, val refundedAmount: BigDecimal, val paymentId: UUID?,
    val paymentReference: String?, val paymentAmount: BigDecimal?, val refundableFromPayment: BigDecimal?, val customerName: String?,
    val customerEmail: String?, val createdAt: Instant, val approvedAt: Instant?, val attempts: List<RefundAttemptView>
)
