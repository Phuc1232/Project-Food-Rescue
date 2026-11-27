// AC-01..10 (T8.3): FinanceLedgerProperties responsibility.
package com.foodrescue.backend.finance
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
@Component
@ConfigurationProperties("finance")
class FinanceLedgerProperties {
    var ledgerJobEnabled: Boolean = true
    var ledgerScanMs: Long = 30000
    var ledgerBatchSize: Int = 100
    @jakarta.annotation.PostConstruct fun validate() { require(ledgerScanMs > 0 && ledgerBatchSize in 1..1000) }
}
