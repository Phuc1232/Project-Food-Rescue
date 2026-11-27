// AC-01..12 (T8.4): FailStorePayoutRequest responsibility.
package com.foodrescue.backend.finance.dto
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import jakarta.validation.constraints.*
import com.foodrescue.backend.finance.PayoutStatus
data class FailStorePayoutRequest(@field:NotBlank @field:Size(max=2000) val reason: String?)
