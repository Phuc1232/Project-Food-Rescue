// AC-01..12 (T8.4): UpsertPayoutAccountRequest responsibility.
package com.foodrescue.backend.finance.dto
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import jakarta.validation.constraints.*
import com.foodrescue.backend.finance.PayoutStatus
data class UpsertPayoutAccountRequest(@field:NotBlank @field:Size(max=100) val bankCode: String?, @field:NotBlank @field:Size(max=100) val accountNumber: String?, @field:NotBlank @field:Size(min=2,max=255) val accountHolderName: String?)
