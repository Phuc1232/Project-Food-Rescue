// AC-01 (T6.1): WebhookPayment responsibility.
package com.foodrescue.backend.payment.gateway

/**
 * A verified provider notification. transactionCode/description of the transfer itself ("00" = success); reference = bank transaction
 * reference (unique per transaction, used for idempotency with provider). transactionDateTime as sent ("yyyy-MM-dd HH:mm:ss", Vietnam time).
 */
data class WebhookPayment(val orderCode: Long, val amount: Long, val reference: String, val paymentLinkId: String?, val transactionCode: String,
                          val transactionDateTime: String?, val currency: String, val success: Boolean)
