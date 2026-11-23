// AC-03,09 (T7.1): PickupCodeView exposes a customer's own pickup credential and snapshots.
package com.foodrescue.backend.pickup.dto

import com.foodrescue.backend.order.dto.OrderMoneyView
import java.time.Instant
import java.util.UUID

data class PickupCodeView(val orderId: UUID, val orderCode: String, val orderStatus: String, val code: String?, val usable: Boolean,
                          val pickupAddress: String, val pickupStartsAt: Instant, val pickupEndsAt: Instant, val money: OrderMoneyView)
