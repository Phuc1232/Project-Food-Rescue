// AC-02..05 (T4.5): FinancialPolicyRequest responsibility.
package com.foodrescue.backend.finance.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant

/** Rates in [0,1] with at most 4 decimals; disputeWindowHours must be 48 (BR-43); basedOnVersion = latest version the admin reviewed. */
data class FinancialPolicyRequest(
    @field:NotNull val basedOnVersion: Int?,
    @field:NotNull val commissionRate: BigDecimal?,
    @field:NotNull val noShowRetentionRate: BigDecimal?,
    @field:NotNull val disputeWindowHours: Int?,
    @field:NotBlank @field:Size(max = 4000) val cancellationTerms: String?,
    @field:NotBlank @field:Size(max = 4000) val refundTerms: String?,
    val effectiveFrom: Instant? = null,
    @field:NotBlank @field:Size(max = 1000) val reason: String?
)
