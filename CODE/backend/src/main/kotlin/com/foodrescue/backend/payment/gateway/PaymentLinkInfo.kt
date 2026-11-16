// AC-03 (T6.1): PaymentLinkInfo responsibility.
package com.foodrescue.backend.payment.gateway

data class PaymentLinkInfo(val orderCode: Long, val paymentLinkId: String, val amount: Long, val amountPaid: Long, val status: PaymentLinkStatus)
