// AC-05 (T5.1): OfferDetail responsibility.
package com.foodrescue.backend.search.dto

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * UC-10. safetyWarnings: batch warnings plus warnings of the items in boxes that could be allocated (BR-19); never the items themselves.
 * orderable=false with unavailableReason PAUSED, ENDED, SOLD_OUT or DEADLINE_PASSED (UC-10 A2).
 */
data class OfferDetail(
    val batchId: UUID, val status: String, val orderable: Boolean, val unavailableReason: String?, val boxName: String, val description: String,
    val categoryCode: String, val categoryName: String, val sellingPrice: BigDecimal, val minimumValue: BigDecimal, val available: Long,
    val orderDeadline: Instant, val pickupStartsAt: Instant, val pickupEndsAt: Instant, val safetyWarnings: List<String>,
    val storeId: UUID, val storeName: String, val address: String, val latitude: BigDecimal, val longitude: BigDecimal, val timezone: String,
    val directionsUrl: String, val hasCover: Boolean
)
