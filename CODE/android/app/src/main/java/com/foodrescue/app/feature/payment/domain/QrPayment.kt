package com.foodrescue.app.feature.payment.domain

internal enum class PaymentStatus { READY, PROCESSING, SUCCEEDED, FAILED, EXPIRED }

internal data class QrPayment(
    val reference: String,
    val amount: Long,
    val status: PaymentStatus,
    val updatedAtMillis: Long,
) {
    init { require(reference.isNotBlank() && amount > 0 && updatedAtMillis >= 0) }
}
