// AC-01..08 (T8.2): NotificationProperties responsibility.
package com.foodrescue.backend.notification
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
@Component
@ConfigurationProperties("notifications")
class NotificationProperties {
    var dispatchJobEnabled: Boolean = true
    var dispatchScanMs: Long = 10000
    var batchSize: Int = 50
    var leaseSeconds: Long = 60
    var maxAttempts: Int = 3
    var retryDelaysSeconds: List<Long> = listOf(60L,300L,900L)
    var mailFrom: String = ""
    var emailEnabled: Boolean = true
    @jakarta.annotation.PostConstruct fun validate() {
        require(dispatchScanMs>0 && batchSize in 1..1000 && leaseSeconds>0 && maxAttempts>0)
        require(retryDelaysSeconds.isNotEmpty() && retryDelaysSeconds.all { it>0 })
    }
}
