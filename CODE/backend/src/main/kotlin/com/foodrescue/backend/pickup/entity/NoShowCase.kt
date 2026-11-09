package com.foodrescue.backend.pickup.entity

import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.common.persistence.MutableEntity
import com.foodrescue.backend.notification.entity.Notification
import com.foodrescue.backend.order.entity.CustomerOrder
import com.foodrescue.backend.pickup.CompensationStatus
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "no_show_case")
class NoShowCase(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "order_id", nullable = false)
    var order: CustomerOrder,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "verified_by_id", nullable = false)
    var verifiedBy: Account,

    @field:Column(name = "verified_at", nullable = false)
    var verifiedAt: Instant,

    @field:Column(name = "evidence", nullable = false, columnDefinition = "text")
    var evidence: String,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = true)
    @field:JoinColumn(name = "notification_id", nullable = true)
    var notification: Notification? = null,

    @field:Column(name = "notification_received_at", nullable = true)
    var notificationReceivedAt: Instant? = null,

    @field:Column(name = "dispute_deadline", nullable = true)
    var disputeDeadline: Instant? = null,

    @field:Column(name = "retained_amount", nullable = false, precision = 19, scale = 2)
    var retainedAmount: BigDecimal,

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "compensation_status", nullable = false, length = 50)
    var compensationStatus: CompensationStatus
) : MutableEntity()
