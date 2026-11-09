package com.foodrescue.backend.complaint.entity

import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.common.persistence.MutableEntity
import com.foodrescue.backend.complaint.ComplaintStatus
import com.foodrescue.backend.complaint.ComplaintType
import com.foodrescue.backend.order.entity.CustomerOrder
import com.foodrescue.backend.store.entity.Store
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "complaint")
class Complaint(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "store_id", nullable = false)
    var store: Store,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "order_id", nullable = false)
    var order: CustomerOrder,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "customer_id", nullable = false)
    var customer: Account,

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "type", nullable = false, length = 50)
    var type: ComplaintType,

    @field:Column(name = "description", nullable = false, columnDefinition = "text")
    var description: String,

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "status", nullable = false, length = 50)
    var status: ComplaintStatus,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = true)
    @field:JoinColumn(name = "handled_by_id", nullable = true)
    var handledBy: Account? = null,

    @field:Column(name = "decision", nullable = true, columnDefinition = "text")
    var decision: String? = null,

    @field:Column(name = "resolved_at", nullable = true)
    var resolvedAt: Instant? = null
) : MutableEntity()
