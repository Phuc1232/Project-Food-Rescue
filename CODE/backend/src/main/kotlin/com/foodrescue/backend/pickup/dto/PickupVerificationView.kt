// AC-05 (T7.1): PickupVerificationView exposes the verified order allocation to authorized handlers.
package com.foodrescue.backend.pickup.dto

import com.foodrescue.backend.order.dto.AllocatedBoxView
import com.foodrescue.backend.order.dto.OrderMoneyView
import java.time.Instant
import java.util.UUID

data class PickupVerificationView(val orderId: UUID, val orderCode: String, val orderStatus: String, val storeId: UUID, val quantity: Int,
                                  val pickupAddress: String, val pickupStartsAt: Instant, val pickupEndsAt: Instant,
                                  val boxes: List<AllocatedBoxView>, val money: OrderMoneyView)
