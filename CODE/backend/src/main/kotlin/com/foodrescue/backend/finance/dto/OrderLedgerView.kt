// AC-01..10 (T8.3): OrderLedgerView responsibility.
package com.foodrescue.backend.finance.dto
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import com.foodrescue.backend.common.response.PageResponse
data class OrderLedgerView(val orderId: UUID, val orderCode: String, val collectedVerified: BigDecimal, val refundedVerified: BigDecimal, val netCollected: BigDecimal, val grossCommission: BigDecimal, val commissionAdjustment: BigDecimal, val netCommission: BigDecimal, val grossStorePayable: BigDecimal, val storeAdjustment: BigDecimal, val netStorePayable: BigDecimal, val grossNoShowCompensation: BigDecimal, val noShowAdjustment: BigDecimal, val netNoShowCompensation: BigDecimal, val netAllocated: BigDecimal, val balanced: Boolean, val entries: List<LedgerEntryView>)
