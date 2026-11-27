// AC-01..10 (T8.3): LedgerPostingJob responsibility.
package com.foodrescue.backend.finance.service
import org.springframework.stereotype.Component
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
@Component
@ConditionalOnProperty(name=["finance.ledger-job-enabled"],havingValue="true",matchIfMissing=true)
class LedgerPostingJob(private val posting: LedgerPostingService) {
    @Scheduled(fixedDelayString="\${finance.ledger-scan-ms:30000}")
    fun run() { posting.postPendingEvents() }
}
