// AC-01..12 (T8.4): CreateStorePayoutRequest responsibility.
package com.foodrescue.backend.finance.dto
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import jakarta.validation.constraints.*
import com.foodrescue.backend.finance.PayoutStatus
data class CreateStorePayoutRequest(@field:NotNull val payoutAccountId: UUID?, @field:NotNull @field:Min(1) val expectedAccountVersion: Int?, @field:NotNull @field:DecimalMin("1") @field:Digits(integer=17,fraction=0) val amount: BigDecimal?, @field:NotBlank @field:Size(max=255) val transferReference: String?, val status: PayoutStatus = PayoutStatus.VERIFIED, val transferredAt: Instant? = null, val evidenceFileId: UUID? = null, @field:Size(max=2000) val note: String? = null)
