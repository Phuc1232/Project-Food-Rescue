// AC-01..10 (T8.3): StoreOrderRevenueItemView responsibility.
package com.foodrescue.backend.finance.dto
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import com.foodrescue.backend.common.response.PageResponse
data class StoreOrderRevenueItemView(val orderId: UUID, val orderCode: String, val completedAt: Instant?, val commissionRateSnapshot: BigDecimal, val actualSellingPrice: BigDecimal, val grossCommissionAmount: BigDecimal, val commissionAdjustmentAmount: BigDecimal, val netCommissionAmount: BigDecimal, val grossStorePayableAmount: BigDecimal, val storeAdjustmentAmount: BigDecimal, val netStoreRevenueAmount: BigDecimal)
