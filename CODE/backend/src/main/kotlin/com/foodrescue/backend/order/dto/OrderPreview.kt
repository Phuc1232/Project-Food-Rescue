// AC-01 (T5.2): OrderPreview responsibility.
package com.foodrescue.backend.order.dto

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** UC-13 step 4: everything the customer accepts before payment. Nothing is held yet. */
data class OrderPreview(
    val batchId: UUID, val storeName: String, val boxName: String, val quantity: Int, val available: Long, val unitPrice: BigDecimal,
    val totalAmount: BigDecimal, val minimumValuePerBox: BigDecimal, val safetyWarnings: List<String>, val pickupAddress: String,
    val pickupStartsAt: Instant, val pickupEndsAt: Instant, val holdMinutes: Long, val policy: PolicySummary
)
