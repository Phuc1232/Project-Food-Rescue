// AC-01..08 (T8.2): NotificationViews responsibility.
package com.foodrescue.backend.notification.service
import com.foodrescue.backend.notification.entity.Notification
import com.foodrescue.backend.notification.dto.NotificationView
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
@Component
class NotificationViews(private val mapper: ObjectMapper) {
    fun view(n: Notification)=NotificationView(n.id!!,n.order?.id,n.type,n.eventCode,n.title,mapper.readTree(n.payload),n.status,n.createdAt,n.sentAt,n.deliveredAt,n.readAt)
}
