// AC-03 (T6.1): PaymentLink responsibility.
package com.foodrescue.backend.payment.gateway

/** Result of creating a link: checkoutUrl to open, qrCode = VietQR payload string. */
data class PaymentLink(val orderCode: Long, val paymentLinkId: String, val amount: Long, val checkoutUrl: String, val qrCode: String,
                       val status: PaymentLinkStatus)
