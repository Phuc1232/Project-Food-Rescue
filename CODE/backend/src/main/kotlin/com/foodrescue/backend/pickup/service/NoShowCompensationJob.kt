// AC-01..14 (T7.4): NoShowCompensationJob responsibility.
package com.foodrescue.backend.pickup.service
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
@Component
@ConditionalOnProperty(name = ["orders.no-show-compensation-job-enabled"], havingValue = "true", matchIfMissing = true)
class NoShowCompensationJob(private val service: NoShowCompensationService, private val clock: Clock) {
    @Scheduled(fixedDelayString = "\${orders.no-show-compensation-scan-ms:60000}", initialDelayString = "\${orders.no-show-compensation-scan-ms:60000}")
    fun run() { while (service.releaseEligible(clock.instant()) == 100) { /* drain backlog */ } }
}
