// AC-01,02,03 (T5.1): OfferSummary responsibility.
package com.foodrescue.backend.search.dto

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** One sale batch on sale (BR-T51-01). distanceKm only when a position was given. No item list (BR-05). */
data class OfferSummary(
    val batchId: UUID, val storeId: UUID, val storeName: String, val boxName: String, val categoryCode: String, val categoryName: String,
    val sellingPrice: BigDecimal, val minimumValue: BigDecimal, val available: Long, val orderDeadline: Instant, val pickupStartsAt: Instant,
    val pickupEndsAt: Instant, val distanceKm: Double?, val hasCover: Boolean
)
