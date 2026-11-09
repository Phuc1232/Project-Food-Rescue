package com.foodrescue.backend.notification.entity

import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.common.persistence.MutableEntity
import com.foodrescue.backend.notification.NotificationStatus
import com.foodrescue.backend.notification.NotificationType
import com.foodrescue.backend.order.entity.CustomerOrder
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "notification")
class Notification(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "recipient_id", nullable = false)
    var recipient: Account,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = true)
    @field:JoinColumn(name = "order_id", nullable = true)
    var order: CustomerOrder? = null,

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "type", nullable = false, length = 50)
    var type: NotificationType,

    @field:Column(name = "payload", nullable = false, columnDefinition = "text")
    var payload: String,

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "status", nullable = false, length = 50)
    var status: NotificationStatus,

    @field:Column(name = "sent_at", nullable = true)
    var sentAt: Instant? = null,

    @field:Column(name = "delivered_at", nullable = true)
    var deliveredAt: Instant? = null,

    @field:Column(name = "read_at", nullable = true)
    var readAt: Instant? = null
) : MutableEntity()
