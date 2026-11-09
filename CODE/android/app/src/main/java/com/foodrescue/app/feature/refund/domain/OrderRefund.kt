package com.foodrescue.app.feature.refund.domain

internal enum class RefundStatus { PROCESSING, COMPLETED, FAILED }
internal data class OrderRefund(
    val amount: Long,
    val status: RefundStatus,
    val requestedAtMillis: Long,
    val updatedAtMillis: Long,
) {
    init { require(amount > 0 && requestedAtMillis >= 0 && updatedAtMillis >= requestedAtMillis) }
}

internal enum class RefundResult { COMPLETED, UNAVAILABLE }
internal fun interface RefundRepository { fun check(refund: OrderRefund): RefundResult }
