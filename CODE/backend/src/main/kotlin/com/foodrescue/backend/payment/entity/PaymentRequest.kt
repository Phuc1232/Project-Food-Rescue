package com.foodrescue.backend.payment.entity

import com.foodrescue.backend.common.persistence.MutableEntity
import com.foodrescue.backend.order.entity.CustomerOrder
import com.foodrescue.backend.payment.PaymentRequestStatus
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "payment_request")
class PaymentRequest(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "order_id", nullable = false)
    var order: CustomerOrder,

    @field:Column(name = "provider", nullable = false, length = 255)
    var provider: String,

    @field:Column(name = "provider_request_id", nullable = true, length = 255)
    var providerRequestId: String? = null,

    @field:Column(name = "idempotency_key", nullable = false, length = 255)
    var idempotencyKey: String,

    @field:Column(name = "expected_amount", nullable = false, precision = 19, scale = 2)
    var expectedAmount: BigDecimal,

    @field:Column(name = "currency", nullable = false, length = 3)
    var currency: String,

    @field:Column(name = "payment_url", nullable = true, columnDefinition = "text")
    var paymentUrl: String? = null,

    @field:Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "status", nullable = false, length = 50)
    var status: PaymentRequestStatus
) : MutableEntity()
