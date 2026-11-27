// AC-01..10 (T8.3): StoreRevenueSummaryView responsibility.
package com.foodrescue.backend.finance.dto
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import com.foodrescue.backend.common.response.PageResponse
data class StoreRevenueSummaryView(val completedOrderCount: Int, val grossSalesAmount: BigDecimal, val grossCommissionAmount: BigDecimal, val commissionAdjustmentAmount: BigDecimal, val netCommissionAmount: BigDecimal, val storeAdjustmentAmount: BigDecimal, val netStoreRevenueAmount: BigDecimal, val currency: String = "VND")
