package com.foodrescue.backend.order.entity

import com.foodrescue.backend.common.persistence.MutableEntity
import com.foodrescue.backend.order.AllocationStatus
import com.foodrescue.backend.order.entity.CustomerOrder
import com.foodrescue.backend.store.entity.Store
import com.foodrescue.backend.surprisebox.entity.PhysicalBox
import com.foodrescue.backend.surprisebox.entity.SaleBatch
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "box_allocation")
class BoxAllocation(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "store_id", nullable = false)
    var store: Store,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "order_id", nullable = false)
    var order: CustomerOrder,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "box_id", nullable = false)
    var box: PhysicalBox,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "sale_batch_at_allocation_id", nullable = false)
    var saleBatchAtAllocation: SaleBatch,

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "status", nullable = false, length = 50)
    var status: AllocationStatus,

    @field:Column(name = "reserved_at", nullable = false)
    var reservedAt: Instant,

    @field:Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    @field:Column(name = "confirmed_at", nullable = true)
    var confirmedAt: Instant? = null,

    @field:Column(name = "released_at", nullable = true)
    var releasedAt: Instant? = null,

    @field:Column(name = "release_reason", nullable = true, columnDefinition = "text")
    var releaseReason: String? = null
) : MutableEntity()
