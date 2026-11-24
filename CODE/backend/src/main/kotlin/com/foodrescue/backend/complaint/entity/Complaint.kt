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

    @field:Column(name = "complaint_code", nullable = false, length = 64)
    var complaintCode: String,
    @field:Column(name = "within_dispute_window", nullable = false)
    var withinDisputeWindow: Boolean = true,
    @field:Column(name = "requested_info_note", columnDefinition = "text")
    var requestedInfoNote: String? = null,
    @field:Enumerated(EnumType.STRING) @field:Column(name = "outcome", length = 32)
    var outcome: com.foodrescue.backend.complaint.ComplaintOutcome? = null,
    @field:Enumerated(EnumType.STRING) @field:Column(name = "responsibility", length = 32)
    var responsibility: com.foodrescue.backend.complaint.ComplaintResponsibility? = null,
    @field:Column(name = "approved_refund_amount", precision = 19, scale = 2)
    var approvedRefundAmount: java.math.BigDecimal? = null,
    @field:Column(name = "store_adjustment_amount", precision = 19, scale = 2)
    var storeAdjustmentAmount: java.math.BigDecimal? = null,
    @field:Column(name = "commission_adjustment_amount", precision = 19, scale = 2)
    var commissionAdjustmentAmount: java.math.BigDecimal? = null,
    @field:Column(name = "closed_at") var closedAt: Instant? = null,
    @field:Column(name = "request_fingerprint", columnDefinition = "text") var requestFingerprint: String? = null,
    @field:Column(name = "decision_key", length = 255) var decisionKey: String? = null,
    @field:Column(name = "decision_fingerprint", columnDefinition = "text") var decisionFingerprint: String? = null,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = true)
    @field:JoinColumn(name = "handled_by_id", nullable = true)
    var handledBy: Account? = null,

    @field:Column(name = "decision", nullable = true, columnDefinition = "text")
    var decision: String? = null,

    @field:Column(name = "resolved_at", nullable = true)
    var resolvedAt: Instant? = null,
    @field:ManyToOne(fetch = FetchType.LAZY) @field:JoinColumn(name = "source_box_id")
    var sourceBox: com.foodrescue.backend.surprisebox.entity.PhysicalBox? = null,
    @field:Column(name = "idempotency_key", length = 255) var idempotencyKey: String? = null
) : MutableEntity()
