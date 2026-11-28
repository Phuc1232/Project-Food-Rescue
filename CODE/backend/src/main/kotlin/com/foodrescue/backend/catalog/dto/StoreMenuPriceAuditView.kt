// AC-01..07 (T8.5): StoreMenuPriceAuditView responsibility.
package com.foodrescue.backend.catalog.dto
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import com.foodrescue.backend.common.response.PageResponse
import tools.jackson.databind.JsonNode
data class StoreMenuPriceAuditView(val storeId: UUID, val storeName: String, val storeStatus: String, val items: PageResponse<MenuItemPriceAuditView>)
