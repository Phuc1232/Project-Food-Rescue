package com.foodrescue.backend.pickup.entity

import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.common.persistence.MutableEntity
import com.foodrescue.backend.order.entity.CustomerOrder
import com.foodrescue.backend.pickup.PickupStatus
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "pickup_session")
class PickupSession(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "order_id", nullable = false)
    var order: CustomerOrder,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "actor_id", nullable = false)
    var actor: Account,

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "status", nullable = false, length = 50)
    var status: PickupStatus,

    @field:Column(name = "started_at", nullable = false)
    var startedAt: Instant,

    @field:Column(name = "completed_at", nullable = true)
    var completedAt: Instant? = null,

    @field:Column(name = "idempotency_key", nullable = false, length = 255)
    var idempotencyKey: String
) : MutableEntity()
