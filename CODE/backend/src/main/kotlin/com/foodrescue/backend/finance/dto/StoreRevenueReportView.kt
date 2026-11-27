// AC-01..10 (T8.3): StoreRevenueReportView responsibility.
package com.foodrescue.backend.finance.dto
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import com.foodrescue.backend.common.response.PageResponse
data class StoreRevenueReportView(val summary: StoreRevenueSummaryView, val orders: PageResponse<StoreOrderRevenueItemView>)
