// AC-01..07 (T8.5): AdminFinanceStatisticsView responsibility.
package com.foodrescue.backend.finance.dto
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import com.foodrescue.backend.common.response.PageResponse
import tools.jackson.databind.JsonNode
data class AdminFinanceStatisticsView(val scopeStoreId: UUID?, val from: Instant?, val to: Instant?, val totalOrderCount: Long, val orderCountsByStatus: Map<String, Long>, val ledger: AdminFinanceLedgerStatsView, val settlementAndHold: AdminFinanceSettlementStatsView, val byStore: List<AdminStoreFinanceStatsItem>)
