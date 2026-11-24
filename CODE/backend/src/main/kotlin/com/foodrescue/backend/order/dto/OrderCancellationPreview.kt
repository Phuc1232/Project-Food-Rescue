// AC-01..15 (T7.3): OrderCancellationPreview responsibility.
package com.foodrescue.backend.order.dto

import java.util.UUID
import java.time.Instant
import java.math.BigDecimal
import jakarta.validation.constraints.*
import com.foodrescue.backend.order.CancellationDecision
import com.foodrescue.backend.payment.dto.RefundRequestView
import com.foodrescue.backend.pickup.dto.PickupIncidentView

data class OrderCancellationPreview(val orderId: UUID, val orderStatus: String, val cancellableImmediately: Boolean,
    val requiresReview: Boolean, val estimatedRefundAmount: BigDecimal?, val blockedReason: String?, val policy: PolicySummary)
