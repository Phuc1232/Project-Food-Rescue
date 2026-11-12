package com.foodrescue.app.feature.payment.domain

internal enum class PaymentResult { CONFIRMED, UNAVAILABLE }

/** Frontend result source. Real payment confirmation must come from a verified server response. */
internal fun interface PaymentRepository {
    fun check(payment: QrPayment): PaymentResult
}
