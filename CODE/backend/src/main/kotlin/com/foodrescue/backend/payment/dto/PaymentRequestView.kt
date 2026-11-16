// AC-01,02,06 (T6.2): PaymentRequestView responsibility.
package com.foodrescue.backend.payment.dto

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * What the app shows to pay (UC-15 step 4). status READY = link usable; PENDING = provider answer not confirmed yet (try again).
 * orderStatus tells whether the payment was verified — returning to the app never means paid (UC-14 A3).
 */
data class PaymentRequestView(
    val id: UUID, val orderId: UUID, val orderCode: String, val providerOrderCode: Long?, val provider: String, val status: String,
    val amount: BigDecimal, val currency: String, val checkoutUrl: String?, val qrCode: String?, val transferDescription: String?,
    val expiresAt: Instant, val orderStatus: String
)
