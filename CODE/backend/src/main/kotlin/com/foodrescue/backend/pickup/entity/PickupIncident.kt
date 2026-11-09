package com.foodrescue.backend.pickup.entity

import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.common.persistence.MutableEntity
import com.foodrescue.backend.order.entity.CustomerOrder
import com.foodrescue.backend.pickup.entity.PickupSession
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "pickup_incident")
class PickupIncident(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "order_id", nullable = false)
    var order: CustomerOrder,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = true)
    @field:JoinColumn(name = "session_id", nullable = true)
    var session: PickupSession? = null,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "actor_id", nullable = false)
    var actor: Account,

    @field:Column(name = "reason", nullable = false, columnDefinition = "text")
    var reason: String,

    @field:Column(name = "customer_arrived", nullable = false)
    var customerArrived: Boolean,

    @field:Column(name = "occurred_at", nullable = false)
    var occurredAt: Instant,

    @field:Column(name = "resolution", nullable = true, columnDefinition = "text")
    var resolution: String? = null
) : MutableEntity()
