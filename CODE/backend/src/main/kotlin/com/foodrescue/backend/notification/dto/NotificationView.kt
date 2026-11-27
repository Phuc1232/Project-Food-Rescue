// AC-01..08 (T8.2): NotificationView responsibility.
package com.foodrescue.backend.notification.dto
import com.foodrescue.backend.notification.*
import tools.jackson.databind.JsonNode
import java.util.UUID
import java.time.Instant
data class NotificationView(val id: UUID,val orderId: UUID?,val type: NotificationType,val eventCode: String?,val title: String?,
    val payload: JsonNode,val status: NotificationStatus,val createdAt: Instant,val sentAt: Instant?,val deliveredAt: Instant?,val readAt: Instant?)
