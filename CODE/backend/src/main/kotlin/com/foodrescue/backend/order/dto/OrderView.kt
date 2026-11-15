// AC-02,05,08 (T5.2): OrderView responsibility.
package com.foodrescue.backend.order.dto

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** The order as accepted (snapshot); amounts are whole VND. */
data class OrderView(
    val id: UUID, val orderCode: String, val status: String, val storeId: UUID, val storeName: String, val batchId: UUID, val quantity: Int,
    val unitPrice: BigDecimal, val totalAmount: BigDecimal, val minimumValuePerBox: BigDecimal, val currency: String, val safetyWarnings: String,
    val pickupAddress: String, val pickupStartsAt: Instant, val pickupEndsAt: Instant, val holdExpiresAt: Instant, val acceptedAt: Instant,
    val policy: PolicySummary
)
