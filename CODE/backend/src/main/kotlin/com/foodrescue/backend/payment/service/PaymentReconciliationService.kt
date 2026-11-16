// AC-03 (T6.4): PaymentReconciliationService responsibility.
package com.foodrescue.backend.payment.service

import com.foodrescue.backend.payment.dto.ReconciliationReport
import com.foodrescue.backend.payment.gateway.PaymentGateway
import com.foodrescue.backend.payment.gateway.PaymentGatewayException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Reconciliation (BR-30): for live payment requests, closes those whose order ended and asks the provider about the rest. Provider calls happen
 * outside transactions (CLAUDE.md §5); every database change is re-checked under locks, so running it again changes nothing more and never
 * creates payments or obligations.
 */
@Service
class PaymentReconciliationService(private val store: ReconciliationStore, private val gateway: PaymentGateway) {
    private val log = LoggerFactory.getLogger(PaymentReconciliationService::class.java)

    fun run(): ReconciliationReport {
        var expired = 0; var released = 0; var missing = 0; var errors = 0
        val candidates = store.candidates()
        for ((id, code) in candidates) {
            if (store.closeIfOrderEnded(id)) {
                expired++
                try { gateway.cancelLink(code, "Order ended") } catch (_: PaymentGatewayException) { /* best effort: the link expires anyway */ }
                continue
            }
            val info = try { gateway.getLink(code) } catch (e: PaymentGatewayException) { errors++; log.warn("Reconciliation lookup failed for {}: {}", code, e.kind); continue }
            when (store.applyProviderStatus(id, info.status, info.amountPaid)) {
                ReconcileAction.RELEASED_ORDER -> { released++; expired++ }
                ReconcileAction.MISSING_WEBHOOK -> missing++
                else -> {}
            }
        }
        return ReconciliationReport(candidates.size, expired, released, missing, errors)
    }
}
