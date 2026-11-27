// AC-01..08 (T8.2): NotificationDispatchService responsibility.
package com.foodrescue.backend.notification.service
import com.foodrescue.backend.notification.NotificationProperties
import com.foodrescue.backend.notification.port.*
import com.foodrescue.backend.notification.dto.NotificationDispatchReport
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.*
import java.time.Clock
import java.time.Instant
@Service
class NotificationDispatchService(private val store: NotificationDispatchStore,private val sender: NotificationSender,
    private val properties: NotificationProperties,private val clock: Clock) {
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    fun dispatchDue(now: Instant,batchSize: Int=properties.batchSize): NotificationDispatchReport {
        require(batchSize in 1..1000)
        var claimed=0;var delivered=0;var sent=0;var failed=0
        // Claim immediately before each send: a batch must not consume its leases while waiting for earlier SMTP calls.
        repeat(batchSize) {
            val command=store.claimDue(maxOf(now,clock.instant()),1).firstOrNull()
                ?: return NotificationDispatchReport(claimed,delivered,sent,failed)
            claimed++
            val result=try { sender.send(command) } catch(_: Exception) {
                store.markFailure(command,maxOf(now,clock.instant()),"Notification delivery failed")
                failed++;return@repeat
            }
            store.markSuccess(command,maxOf(now,clock.instant()),result)
            if(result.outcome==NotificationDeliveryOutcome.DELIVERED) delivered++ else sent++
        }
        return NotificationDispatchReport(claimed,delivered,sent,failed)
    }
}
