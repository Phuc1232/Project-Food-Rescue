// AC-01..14 (T7.4): NoShowOverdueJob responsibility.
package com.foodrescue.backend.pickup.service
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
@Component
@ConditionalOnProperty(name = ["orders.no-show-job-enabled"], havingValue = "true", matchIfMissing = true)
class NoShowOverdueJob(private val service: NoShowOverdueService, private val clock: Clock) {
    @Scheduled(fixedDelayString = "\${orders.no-show-scan-ms:30000}", initialDelayString = "\${orders.no-show-scan-ms:30000}")
    fun run() { while (service.markOverdue(clock.instant()) == 100) { /* drain backlog */ } }
}
