package com.foodrescue.backend.payment.entity

import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.common.persistence.MutableEntity
import com.foodrescue.backend.complaint.entity.Complaint
import com.foodrescue.backend.order.entity.CustomerOrder
import com.foodrescue.backend.payment.RefundRequestStatus
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "refund_request")
class RefundRequest(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "order_id", nullable = false)
    var order: CustomerOrder,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = true)
    @field:JoinColumn(name = "complaint_id", nullable = true)
    var complaint: Complaint? = null,

    @field:Column(name = "reason", nullable = false, columnDefinition = "text")
    var reason: String,

    @field:Column(name = "requested_amount", nullable = false, precision = 19, scale = 2)
    var requestedAmount: BigDecimal,

    @field:Column(name = "approved_amount", nullable = true, precision = 19, scale = 2)
    var approvedAmount: BigDecimal? = null,

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "status", nullable = false, length = 50)
    var status: RefundRequestStatus,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = true)
    @field:JoinColumn(name = "approved_by_id", nullable = true)
    var approvedBy: Account? = null,

    @field:Column(name = "approved_at", nullable = true)
    var approvedAt: Instant? = null
) : MutableEntity()
