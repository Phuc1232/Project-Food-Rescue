// AC-01..07 (T8.5): AdminFinanceLedgerStatsView responsibility.
package com.foodrescue.backend.finance.dto
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import com.foodrescue.backend.common.response.PageResponse
import tools.jackson.databind.JsonNode
data class AdminFinanceLedgerStatsView(val verifiedCollectedAmount: BigDecimal, val verifiedRefundedAmount: BigDecimal, val netCashFlowAmount: BigDecimal, val completedOrderGrossRevenue: BigDecimal, val grossCommissionAmount: BigDecimal, val commissionAdjustmentAmount: BigDecimal, val netCommissionAmount: BigDecimal, val grossStorePayableAmount: BigDecimal, val storePayableAdjustmentAmount: BigDecimal, val netStorePayableAmount: BigDecimal, val grossNoShowCompensationAmount: BigDecimal, val noShowAdjustmentAmount: BigDecimal, val netNoShowCompensationAmount: BigDecimal, val totalStoreEntitlementNetAmount: BigDecimal, val totalLedgerAllocatedAmount: BigDecimal, val completedOrderCount: Long)
