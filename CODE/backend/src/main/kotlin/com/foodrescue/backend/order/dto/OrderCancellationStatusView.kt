// AC-01..15 (T7.3): OrderCancellationStatusView responsibility.
package com.foodrescue.backend.order.dto

import java.util.UUID
import java.time.Instant
import java.math.BigDecimal
import jakarta.validation.constraints.*
import com.foodrescue.backend.order.CancellationDecision
import com.foodrescue.backend.payment.dto.RefundRequestView
import com.foodrescue.backend.pickup.dto.PickupIncidentView

data class OrderCancellationStatusView(val orderId: UUID, val orderStatus: String,
    val requests: List<OrderCancellationView>, val refunds: List<RefundRequestView>)
