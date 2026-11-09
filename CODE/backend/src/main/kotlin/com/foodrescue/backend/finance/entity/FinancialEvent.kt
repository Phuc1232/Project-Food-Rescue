package com.foodrescue.backend.finance.entity

import com.foodrescue.backend.common.persistence.BaseEntity
import com.foodrescue.backend.complaint.entity.Complaint
import com.foodrescue.backend.finance.FinancialEventType
import com.foodrescue.backend.order.entity.CustomerOrder
import com.foodrescue.backend.payment.entity.Payment
import com.foodrescue.backend.payment.entity.Refund
import com.foodrescue.backend.pickup.entity.NoShowCase
import jakarta.persistence.*
import java.time.Instant
import org.hibernate.annotations.Immutable

@Entity
@Table(name = "financial_event")
@Immutable
class FinancialEvent(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "order_id", nullable = false)
    val order: CustomerOrder,

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "type", nullable = false, length = 50)
    val type: FinancialEventType,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = true)
    @field:JoinColumn(name = "payment_id", nullable = true)
    val payment: Payment? = null,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = true)
    @field:JoinColumn(name = "refund_id", nullable = true)
    val refund: Refund? = null,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = true)
    @field:JoinColumn(name = "no_show_case_id", nullable = true)
    val noShowCase: NoShowCase? = null,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = true)
    @field:JoinColumn(name = "complaint_id", nullable = true)
    val complaint: Complaint? = null,

    @field:Column(name = "occurred_at", nullable = false)
    val occurredAt: Instant,

    @field:Column(name = "business_key", nullable = false, length = 255)
    val businessKey: String
) : BaseEntity()
