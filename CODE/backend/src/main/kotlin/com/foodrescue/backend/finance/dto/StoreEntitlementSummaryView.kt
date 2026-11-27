// AC-01..10 (T8.3): StoreEntitlementSummaryView responsibility.
package com.foodrescue.backend.finance.dto
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import com.foodrescue.backend.common.response.PageResponse
data class StoreEntitlementSummaryView(val salesPayableEligibleAmount: BigDecimal, val noShowCompensationEligibleAmount: BigDecimal, val totalEligibleToSettleAmount: BigDecimal, val heldNoShowAmount: BigDecimal, val heldDisputedSalesAmount: BigDecimal, val totalHeldAmount: BigDecimal, val forfeitedNoShowAmount: BigDecimal, val currency: String = "VND")
