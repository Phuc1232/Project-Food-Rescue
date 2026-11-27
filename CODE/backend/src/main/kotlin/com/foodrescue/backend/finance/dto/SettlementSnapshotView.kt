// AC-01..12 (T8.4): SettlementSnapshotView responsibility.
package com.foodrescue.backend.finance.dto
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import jakarta.validation.constraints.*
import com.foodrescue.backend.finance.PayoutStatus
data class SettlementSnapshotView(val heldItems: List<HeldExceptionView>, val unresolvedExceptions: List<HeldExceptionView>)
