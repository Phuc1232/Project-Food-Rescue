// AC-01..12 (T8.4): StorePayoutView responsibility.
package com.foodrescue.backend.finance.dto
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import jakarta.validation.constraints.*
import com.foodrescue.backend.finance.PayoutStatus
data class StorePayoutView(val id: UUID, val settlementId: UUID, val payoutAccountId: UUID, val payoutAccountVersion: Int, val recipientSnapshot: String, val amount: BigDecimal, val transferReference: String, val status: String, val transferredAt: Instant?, val confirmedAt: Instant?, val failureReason: String?, val note: String?, val evidenceFileIds: List<UUID>)
