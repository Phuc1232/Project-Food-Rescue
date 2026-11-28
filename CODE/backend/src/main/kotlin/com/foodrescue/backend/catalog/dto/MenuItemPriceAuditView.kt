// AC-01..07 (T8.5): MenuItemPriceAuditView responsibility.
package com.foodrescue.backend.catalog.dto
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import com.foodrescue.backend.common.response.PageResponse
import tools.jackson.databind.JsonNode
data class MenuItemPriceAuditView(val item: MenuItemView, val priceHistory: List<MenuPriceAuditHistoryItem>, val documents: List<PriceDocumentView>, val packedBoxPriceSummary: List<PackedBoxPriceSummaryView>, val hasPackedPriceMismatch: Boolean)
