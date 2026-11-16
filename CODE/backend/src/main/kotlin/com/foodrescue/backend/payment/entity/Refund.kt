package com.foodrescue.backend.payment.entity

import com.foodrescue.backend.common.persistence.MutableEntity
import com.foodrescue.backend.payment.RefundStatus
import com.foodrescue.backend.payment.entity.Payment
import com.foodrescue.backend.payment.entity.RefundRequest
import jakarta.persistence.*
import com.foodrescue.backend.order.entity.CustomerOrder
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "refund")
class Refund(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "order_id", nullable = false)
    var order: CustomerOrder,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "request_id", nullable = false)
    var request: RefundRequest,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "original_payment_id", nullable = false)
    var originalPayment: Payment,

    @field:Column(name = "amount", nullable = false, precision = 19, scale = 2)
    var amount: BigDecimal,

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "status", nullable = false, length = 50)
    var status: RefundStatus,

    @field:Column(name = "provider_refund_id", nullable = true, length = 255)
    var providerRefundId: String? = null,

    @field:Column(name = "idempotency_key", nullable = false, length = 255)
    var idempotencyKey: String,

    @field:Column(name = "verified_at", nullable = true)
    var verifiedAt: Instant? = null,

    // T6.5 (D3): the administrator who recorded the manual bank transfer, when it was made, and a note.
    @field:ManyToOne(fetch = FetchType.LAZY, optional = true)
    @field:JoinColumn(name = "recorded_by_id", nullable = true)
    var recordedBy: com.foodrescue.backend.account.entity.Account? = null,

    @field:Column(name = "transferred_at", nullable = true)
    var transferredAt: Instant? = null,

    @field:Column(name = "note", nullable = true, columnDefinition = "text")
    var note: String? = null
) : MutableEntity()
