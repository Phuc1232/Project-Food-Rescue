// AC-01..08 (T8.2): NotificationDispatchStore responsibility.
package com.foodrescue.backend.notification.service
import com.foodrescue.backend.notification.*
import com.foodrescue.backend.notification.repository.NotificationRepository
import com.foodrescue.backend.notification.port.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.sql.Timestamp
import java.util.UUID
@Service
class NotificationDispatchStore(private val notifications: NotificationRepository,private val jdbc: NamedParameterJdbcTemplate,
    private val properties: NotificationProperties) {
    @Transactional
    fun claimDue(now: Instant,batchSize: Int): List<NotificationDispatchCommand> {
        require(batchSize in 1..1000)
        val ids=jdbc.queryForList("""select id from notification where status='PENDING' and (next_attempt_at is null or next_attempt_at<=:now)
            order by created_at,id limit :n for update skip locked""",mapOf("now" to Timestamp.from(now),"n" to batchSize),UUID::class.java).filterNotNull()
        val until=now.plusSeconds(properties.leaseSeconds).truncatedTo(ChronoUnit.MICROS)
        return ids.map { id -> val n=notifications.findForUpdate(id)!!;n.nextAttemptAt=until
            NotificationDispatchCommand(id,n.recipient.id!!,n.recipient.loginIdentifier,n.recipient.displayName,n.order?.id,n.type,n.eventCode,n.title,n.payload,n.dedupeKey,until)
        }
    }
    @Transactional
    fun markSuccess(command: NotificationDispatchCommand,now: Instant,result: NotificationDeliveryResult) {
        val n=notifications.findForUpdate(command.notificationId) ?: return
        if(n.status!=NotificationStatus.PENDING || n.nextAttemptAt!=command.claimUntil) return
        n.attemptCount++;n.lastError=null;n.sentAt=n.sentAt ?: now;n.nextAttemptAt=null
        n.status=if(result.outcome==NotificationDeliveryOutcome.DELIVERED) NotificationStatus.DELIVERED else NotificationStatus.SENT
        if(n.status==NotificationStatus.DELIVERED) n.deliveredAt=n.deliveredAt ?: now
    }
    @Transactional
    fun markFailure(command: NotificationDispatchCommand,now: Instant,message: String) {
        val n=notifications.findForUpdate(command.notificationId) ?: return
        if(n.status!=NotificationStatus.PENDING || n.nextAttemptAt!=command.claimUntil) return
        n.attemptCount++;n.lastError=message.take(500)
        if(n.attemptCount>=properties.maxAttempts) { n.status=NotificationStatus.FAILED;n.nextAttemptAt=null }
        else n.nextAttemptAt=now.plusSeconds(properties.retryDelaysSeconds[(n.attemptCount-1).coerceAtMost(properties.retryDelaysSeconds.lastIndex)])
    }
}
