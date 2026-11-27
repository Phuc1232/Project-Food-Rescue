// AC-01..12 (T8.4): SettlementSummaryView responsibility.
package com.foodrescue.backend.finance.dto
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import jakarta.validation.constraints.*
import com.foodrescue.backend.finance.PayoutStatus
data class SettlementSummaryView(val id: UUID, val storeId: UUID, val settlementCode: String?, val periodStartsAt: Instant, val periodEndsAt: Instant, val status: String, val totalAmount: BigDecimal, val payableAmount: BigDecimal, val recoveryAmount: BigDecimal, val carriedOverRecoveryAmount: BigDecimal, val verifiedPaidAmount: BigDecimal, val pendingPayoutAmount: BigDecimal, val remainingPayableAmount: BigDecimal, val confirmedAt: Instant?)
