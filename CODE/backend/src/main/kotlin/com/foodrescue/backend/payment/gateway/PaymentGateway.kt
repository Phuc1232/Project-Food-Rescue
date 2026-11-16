// AC-01..05 (T6.1): PaymentGateway responsibility.
package com.foodrescue.backend.payment.gateway

/**
 * Port to the payment provider (roadmap D2). Implementations call the network: never call them inside a database transaction
 * (CLAUDE.md §5 — write the intent first, call outside, record the result after).
 */
interface PaymentGateway {
    val provider: String

    /** Creates a payment link/QR for one order. */
    fun createLink(request: PaymentLinkRequest): PaymentLink

    /** Looks a link up by the numeric order code (used after an unclear failure, before creating again — UC-15 E1). */
    fun getLink(orderCode: Long): PaymentLinkInfo

    fun cancelLink(orderCode: Long, reason: String): PaymentLinkInfo

    /** Verifies the signature first; throws [InvalidSignatureException] and parses nothing else when it does not match. */
    fun verifyWebhook(rawBody: String): WebhookPayment
}
