// AC-01..07 (T8.5): MenuPriceAuditHistoryItem responsibility.
package com.foodrescue.backend.catalog.dto
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import com.foodrescue.backend.common.response.PageResponse
import tools.jackson.databind.JsonNode
data class MenuPriceAuditHistoryItem(val previousPrice: BigDecimal, val newPrice: BigDecimal, val changedAt: Instant, val changedById: UUID, val changedByDisplayName: String, val reason: String)
