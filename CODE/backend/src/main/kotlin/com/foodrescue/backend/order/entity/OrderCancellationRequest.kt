// AC-01..15 (T7.3): OrderCancellationRequest responsibility.
package com.foodrescue.backend.order.entity

import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.common.persistence.MutableEntity
import com.foodrescue.backend.order.CancellationInitiator
import com.foodrescue.backend.order.CancellationRequestStatus
import com.foodrescue.backend.payment.entity.RefundRequest
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "order_cancellation_request")
class OrderCancellationRequest(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false) @field:JoinColumn(name = "order_id", nullable = false)
    val order: CustomerOrder,
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false) @field:JoinColumn(name = "requested_by_id", nullable = false)
    val requestedBy: Account,
    @field:Enumerated(EnumType.STRING) @field:Column(nullable = false, length = 50)
    val initiator: CancellationInitiator,
    @field:Enumerated(EnumType.STRING) @field:Column(nullable = false, length = 50)
    var status: CancellationRequestStatus,
    @field:Column(nullable = false, columnDefinition = "text") var reason: String,
    @field:Column(name = "store_fault", nullable = false) var storeFault: Boolean,
    @field:Column(name = "requested_at", nullable = false) val requestedAt: Instant,
    @field:Column(name = "idempotency_key", length = 255) val idempotencyKey: String? = null,
    @field:Column(name = "request_fingerprint", nullable = false, columnDefinition = "text") val requestFingerprint: String,
    @field:ManyToOne(fetch = FetchType.LAZY) @field:JoinColumn(name = "reviewer_id") var reviewer: Account? = null,
    @field:Column(name = "reviewed_at") var reviewedAt: Instant? = null,
    @field:Column(name = "review_note", columnDefinition = "text") var reviewNote: String? = null,
    @field:Column(name = "approved_refund_amount", precision = 19, scale = 2) var approvedRefundAmount: BigDecimal? = null,
    @field:ManyToOne(fetch = FetchType.LAZY) @field:JoinColumn(name = "refund_request_id") var refundRequest: RefundRequest? = null,
    @field:Column(name = "decision_key", length = 255) var decisionKey: String? = null,
    @field:Column(name = "decision_fingerprint", columnDefinition = "text") var decisionFingerprint: String? = null
) : MutableEntity()
