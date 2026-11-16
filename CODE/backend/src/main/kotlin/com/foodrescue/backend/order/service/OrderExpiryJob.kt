// AC-01,02 (T5.3): OrderExpiryJob responsibility.
package com.foodrescue.backend.order.service

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock

/** Runs [OrderExpiryService] periodically (orders.expiry-scan-ms, default 30 s). Disabled with orders.expiry-job-enabled=false. */
@Component
@ConditionalOnProperty(name = ["orders.expiry-job-enabled"], havingValue = "true", matchIfMissing = true)
class OrderExpiryJob(private val expiry: OrderExpiryService, private val clock: Clock) {
    @Scheduled(fixedDelayString = "\${orders.expiry-scan-ms:30000}", initialDelayString = "\${orders.expiry-scan-ms:30000}")
    fun run() {
        while (expiry.expireDue(clock.instant()) == BATCH) { /* drain the backlog */ }
    }

    private companion object { const val BATCH = 100 }
}
