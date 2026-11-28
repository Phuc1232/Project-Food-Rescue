// AC-01..07 (T8.5): PackedBoxPriceSummaryView responsibility.
package com.foodrescue.backend.catalog.dto
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import com.foodrescue.backend.common.response.PageResponse
import tools.jackson.databind.JsonNode
data class PackedBoxPriceSummaryView(val unitPriceSnapshot: BigDecimal, val boxCount: Long, val totalQuantity: Long, val firstPackedAt: Instant, val lastPackedAt: Instant, val differsFromCurrentListedPrice: Boolean, val priceDeltaFromCurrentListedPrice: BigDecimal)
