package com.foodrescue.backend.payment.entity

import com.foodrescue.backend.common.persistence.MutableEntity
import com.foodrescue.backend.order.entity.CustomerOrder
import com.foodrescue.backend.payment.PaymentStatus
import com.foodrescue.backend.payment.entity.PaymentRequest
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "payment")
class Payment(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = true)
    @field:JoinColumn(name = "request_id", nullable = true)
    var request: PaymentRequest? = null,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = true)
    @field:JoinColumn(name = "order_id", nullable = true)
    var order: CustomerOrder? = null,

    @field:Column(name = "provider", nullable = false, length = 255)
    var provider: String,

    @field:Column(name = "provider_transaction_id", nullable = false, length = 255)
    var providerTransactionId: String,

    @field:Column(name = "received_amount", nullable = false, precision = 19, scale = 2)
    var receivedAmount: BigDecimal,

    @field:Column(name = "currency", nullable = false, length = 3)
    var currency: String,

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "status", nullable = false, length = 50)
    var status: PaymentStatus,

    @field:Column(name = "verified_at", nullable = true)
    var verifiedAt: Instant? = null,

    @field:Column(name = "received_at", nullable = false)
    var receivedAt: Instant
) : MutableEntity()
