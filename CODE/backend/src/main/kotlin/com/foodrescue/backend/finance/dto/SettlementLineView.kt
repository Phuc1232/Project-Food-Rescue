// AC-01..12 (T8.4): SettlementLineView responsibility.
package com.foodrescue.backend.finance.dto
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import jakarta.validation.constraints.*
import com.foodrescue.backend.finance.PayoutStatus
data class SettlementLineView(val id: UUID, val ledgerEntryId: UUID, val orderId: UUID, val orderCode: String, val entryType: String, val amount: BigDecimal, val originalEntryId: UUID?, val originalSettlementId: UUID?)
