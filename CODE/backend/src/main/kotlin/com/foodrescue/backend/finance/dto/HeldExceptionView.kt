// AC-01..12 (T8.4): HeldExceptionView responsibility.
package com.foodrescue.backend.finance.dto
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import jakarta.validation.constraints.*
import com.foodrescue.backend.finance.PayoutStatus
data class HeldExceptionView(val orderId: UUID, val ledgerEntryId: UUID?, val reason: String, val amount: BigDecimal, val detail: String)
