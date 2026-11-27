// AC-03..05: Recording notification transport with failure injection and transaction evidence.
package com.foodrescue.backend.support
import com.foodrescue.backend.notification.port.*
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.*
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
class RecordingNotificationSender : NotificationSender {
    val commands=CopyOnWriteArrayList<NotificationDispatchCommand>()
    val transactionStates=CopyOnWriteArrayList<Boolean>()
    @Volatile var fail=false
    val failForIds=java.util.concurrent.ConcurrentHashMap.newKeySet<UUID>()
    val failRemainingTimes=AtomicInteger(0)
    @Volatile var outcome=NotificationDeliveryOutcome.DELIVERED
    override fun send(command: NotificationDispatchCommand): NotificationDeliveryResult {
        commands.add(command);transactionStates.add(TransactionSynchronizationManager.isActualTransactionActive())
        if(fail || command.notificationId in failForIds || failRemainingTimes.getAndUpdate { if(it>0) it-1 else 0 }>0)
            throw IllegalStateException("SMTP unavailable; sensitive provider detail must not be persisted")
        return NotificationDeliveryResult(outcome)
    }
    fun reset() { commands.clear();transactionStates.clear();fail=false;failForIds.clear();failRemainingTimes.set(0);outcome=NotificationDeliveryOutcome.DELIVERED }
}
@TestConfiguration
class NotificationTestConfiguration { @Bean @Primary fun recordingNotificationSender()=RecordingNotificationSender() }
