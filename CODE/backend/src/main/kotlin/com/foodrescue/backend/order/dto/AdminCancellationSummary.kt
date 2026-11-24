// AC-01..15 (T7.3): AdminCancellationSummary responsibility.
package com.foodrescue.backend.order.dto

import java.util.UUID
import java.time.Instant
import java.math.BigDecimal
import jakarta.validation.constraints.*
import com.foodrescue.backend.order.CancellationDecision
import com.foodrescue.backend.payment.dto.RefundRequestView
import com.foodrescue.backend.pickup.dto.PickupIncidentView

data class AdminCancellationSummary(val id: UUID, val orderId: UUID, val orderCode: String,
    val customerName: String, val status: String, val initiator: String, val reason: String, val requestedAt: Instant)
