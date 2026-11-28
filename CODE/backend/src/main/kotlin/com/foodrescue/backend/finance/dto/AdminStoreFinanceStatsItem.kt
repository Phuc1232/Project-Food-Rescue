// AC-01..07 (T8.5): AdminStoreFinanceStatsItem responsibility.
package com.foodrescue.backend.finance.dto
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import com.foodrescue.backend.common.response.PageResponse
import tools.jackson.databind.JsonNode
data class AdminStoreFinanceStatsItem(val storeId: UUID, val storeName: String, val storeStatus: String, val totalOrderCount: Long, val orderCountsByStatus: Map<String, Long>, val ledger: AdminFinanceLedgerStatsView, val settlementAndHold: AdminFinanceSettlementStatsView)
