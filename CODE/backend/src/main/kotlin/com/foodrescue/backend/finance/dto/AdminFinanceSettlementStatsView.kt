// AC-01..07 (T8.5): AdminFinanceSettlementStatsView responsibility.
package com.foodrescue.backend.finance.dto
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import com.foodrescue.backend.common.response.PageResponse
import tools.jackson.databind.JsonNode
data class AdminFinanceSettlementStatsView(val heldNoShowAmount: BigDecimal, val heldDisputedSalesAmount: BigDecimal, val heldAmount: BigDecimal, val draftUnconfirmedAmount: BigDecimal, val eligibleUnassignedAmount: BigDecimal, val eligibleUnsettledAmount: BigDecimal, val confirmedSettledLedgerAmount: BigDecimal, val confirmedSettlementTotalAmount: BigDecimal, val confirmedSettlementPayableAmount: BigDecimal, val confirmedSettlementRecoveryAmount: BigDecimal, val paidOutVerifiedAmount: BigDecimal, val pendingPayoutAmount: BigDecimal, val confirmedRemainingPayableAmount: BigDecimal, val outstandingRecoveryDebtAmount: BigDecimal, val totalEstimatedPayableAmount: BigDecimal, val confirmedSettlementCount: Long)
