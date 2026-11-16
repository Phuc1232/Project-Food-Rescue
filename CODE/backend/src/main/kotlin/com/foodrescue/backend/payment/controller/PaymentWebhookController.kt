// AC-01..06 (T6.3): PaymentWebhookController responsibility.
package com.foodrescue.backend.payment.controller

import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.payment.gateway.InvalidSignatureException
import com.foodrescue.backend.payment.gateway.PaymentGateway
import com.foodrescue.backend.payment.service.PaymentConfirmationService
import com.foodrescue.backend.payment.service.WebhookOutcome
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * Public endpoint for the provider (no user token; SecurityConfiguration permits only this POST). The signature is checked before
 * anything else; a valid notification is always answered 200 so the provider stops retrying, duplicates included (CLAUDE.md §5).
 */
@RestController
class PaymentWebhookController(private val gateway: PaymentGateway, private val confirmations: PaymentConfirmationService) {
    @PostMapping("/api/v1/payments/webhooks/payos")
    fun payos(@RequestBody(required = false) body: String?): Map<String, Any> {
        val payment = try { gateway.verifyWebhook(body.orEmpty()) }
            catch (_: InvalidSignatureException) { throw ApiException(400, "INVALID_SIGNATURE", "Invalid webhook signature") }
        val outcome = try { confirmations.record(gateway.provider, payment) }
            catch (_: DataIntegrityViolationException) { WebhookOutcome.DUPLICATE }   // same transaction delivered twice at the same instant
        return mapOf("success" to true, "outcome" to outcome.name)
    }
}
