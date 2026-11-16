// AC-05,06 (T5.3): OrderSummary responsibility.
package com.foodrescue.backend.order.dto

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** List row; customerName is filled for store views only (display name, never the email). */
data class OrderSummary(val id: UUID, val orderCode: String, val status: String, val storeId: UUID, val storeName: String, val quantity: Int,
                        val totalAmount: BigDecimal, val pickupStartsAt: Instant, val pickupEndsAt: Instant, val acceptedAt: Instant,
                        val holdExpiresAt: Instant, val customerName: String?)
