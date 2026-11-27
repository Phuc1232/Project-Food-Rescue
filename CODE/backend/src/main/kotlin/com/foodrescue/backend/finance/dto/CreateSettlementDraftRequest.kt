// AC-01..12 (T8.4): CreateSettlementDraftRequest responsibility.
package com.foodrescue.backend.finance.dto
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import jakarta.validation.constraints.*
import com.foodrescue.backend.finance.PayoutStatus
data class CreateSettlementDraftRequest(@field:NotNull val storeId: UUID?, @field:NotNull val periodStartsAt: Instant?, @field:NotNull val periodEndsAt: Instant?)
