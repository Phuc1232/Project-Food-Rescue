// AC-01..12 (T8.4): VerifyPayoutAccountRequest responsibility.
package com.foodrescue.backend.finance.dto
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import jakarta.validation.constraints.*
import com.foodrescue.backend.finance.PayoutStatus
data class VerifyPayoutAccountRequest(@field:Min(1) val expectedVersion: Int? = null, @field:Size(max=2000) val note: String? = null)
