// AC-01..08 (T8.2): NotificationSender responsibility.
package com.foodrescue.backend.notification.port
import com.foodrescue.backend.notification.NotificationType
import java.util.UUID
import java.time.Instant
/** Snapshot: no managed entities or database transaction crosses the delivery boundary.
 * claimUntil fences late completions after lease reclamation; providers can use dedupeKey for idempotency.
 */
data class NotificationDispatchCommand(val notificationId: UUID,val recipientId: UUID,val recipientEmail: String,
    val recipientDisplayName: String,val orderId: UUID?,val type: NotificationType,val eventCode: String?,val title: String?,
    val payloadJson: String,val dedupeKey: String?,val claimUntil: Instant)
enum class NotificationDeliveryOutcome { DELIVERED, SENT }
data class NotificationDeliveryResult(val outcome: NotificationDeliveryOutcome,val detail: String? = null)
interface NotificationSender { fun send(command: NotificationDispatchCommand): NotificationDeliveryResult }
