// AC-01..15 (T7.3): OrderCancellationView responsibility.
package com.foodrescue.backend.order.dto

import java.util.UUID
import java.time.Instant
import java.math.BigDecimal
import jakarta.validation.constraints.*
import com.foodrescue.backend.order.CancellationDecision
import com.foodrescue.backend.payment.dto.RefundRequestView
import com.foodrescue.backend.pickup.dto.PickupIncidentView

data class OrderCancellationView(val id: UUID, val orderId: UUID, val initiator: String, val status: String,
    val reason: String, val storeFault: Boolean, val requestedAt: Instant, val requestedById: UUID,
    val reviewerId: UUID?, val reviewedAt: Instant?, val reviewNote: String?, val approvedRefundAmount: BigDecimal?,
    val refundRequestId: UUID?, val attachmentIds: List<UUID>)
