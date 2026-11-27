// AC-01..08 (T8.2): NotificationDispatchJob responsibility.
package com.foodrescue.backend.notification.service
import com.foodrescue.backend.notification.NotificationProperties
import org.springframework.stereotype.Component
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock
@Component
@ConditionalOnProperty(name=["notifications.dispatch-job-enabled"],havingValue="true",matchIfMissing=true)
class NotificationDispatchJob(private val dispatch: NotificationDispatchService,private val properties: NotificationProperties,private val clock: Clock) {
    @Scheduled(fixedDelayString="\${notifications.dispatch-scan-ms:10000}",initialDelayString="\${notifications.dispatch-scan-ms:10000}")
    fun run() { while(dispatch.dispatchDue(clock.instant()).claimed==properties.batchSize) { /* drain due backlog */ } }
}
