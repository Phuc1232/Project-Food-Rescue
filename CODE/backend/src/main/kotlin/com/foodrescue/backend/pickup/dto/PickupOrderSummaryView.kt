// AC-01..14 (T7.2): PickupOrderSummaryView responsibility.
package com.foodrescue.backend.pickup.dto

import com.foodrescue.backend.order.dto.AllocatedBoxView
import com.foodrescue.backend.order.dto.OrderMoneyView
import java.time.Instant
import java.util.UUID

data class PickupOrderSummaryView(
    val orderId: UUID, val orderCode: String, val status: String, val customerName: String?,
    val quantity: Int, val pickupStartsAt: Instant, val pickupEndsAt: Instant, val pickupAddress: String,
    val boxes: List<AllocatedBoxView>, val money: OrderMoneyView,
    val activeSessionId: UUID?, val currentSessionStatus: String?
)
