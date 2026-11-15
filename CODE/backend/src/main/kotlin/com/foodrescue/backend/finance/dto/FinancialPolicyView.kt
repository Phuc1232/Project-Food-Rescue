// AC-01..03 (T4.5): FinancialPolicyView responsibility.
package com.foodrescue.backend.finance.dto

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** state: ACTIVE (applies now), SCHEDULED (future), SUPERSEDED. rounding: how rate-based amounts are rounded. */
data class FinancialPolicyView(
    val id: UUID, val version: Int, val state: String, val effectiveFrom: Instant, val commissionRate: BigDecimal,
    val noShowRetentionRate: BigDecimal, val disputeWindowHours: Int, val cancellationTerms: String, val refundTerms: String,
    val rounding: String, val reason: String?, val createdById: UUID?, val createdAt: Instant
)
