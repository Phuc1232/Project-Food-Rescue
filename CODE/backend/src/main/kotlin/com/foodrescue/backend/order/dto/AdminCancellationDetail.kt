// AC-01..15 (T7.3): AdminCancellationDetail responsibility.
package com.foodrescue.backend.order.dto

import java.util.UUID
import java.time.Instant
import java.math.BigDecimal
import jakarta.validation.constraints.*
import com.foodrescue.backend.order.CancellationDecision
import com.foodrescue.backend.payment.dto.RefundRequestView
import com.foodrescue.backend.pickup.dto.PickupIncidentView

data class AdminCancellationDetail(val request: OrderCancellationView, val order: OrderView,
    val money: OrderMoneyView, val incidents: List<PickupIncidentView>)
