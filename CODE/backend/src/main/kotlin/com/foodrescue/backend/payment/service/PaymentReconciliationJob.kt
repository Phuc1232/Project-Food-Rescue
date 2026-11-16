// AC-03 (T6.4): PaymentReconciliationJob responsibility.
package com.foodrescue.backend.payment.service

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** payments.reconcile-interval-ms (default 5 min); disabled with payments.reconcile-job-enabled=false. */
@Component
@ConditionalOnProperty(name = ["payments.reconcile-job-enabled"], havingValue = "true", matchIfMissing = true)
class PaymentReconciliationJob(private val reconciliation: PaymentReconciliationService) {
    @Scheduled(fixedDelayString = "\${payments.reconcile-interval-ms:300000}", initialDelayString = "\${payments.reconcile-interval-ms:300000}")
    fun run() { reconciliation.run() }
}
