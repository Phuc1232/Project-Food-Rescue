// AC-01,02 (T5.2): PolicySummary responsibility.
package com.foodrescue.backend.order.dto

import java.math.BigDecimal

/** Policy terms the customer accepts; noShowRetentionAmount = total x rate, HALF_UP to whole VND (T4.5). */
data class PolicySummary(val version: Int, val noShowRetentionRate: BigDecimal, val noShowRetentionAmount: BigDecimal, val disputeWindowHours: Int,
                         val cancellationTerms: String, val refundTerms: String)
