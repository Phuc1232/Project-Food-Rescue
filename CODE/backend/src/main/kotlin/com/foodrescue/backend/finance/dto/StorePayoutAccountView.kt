// AC-01..12 (T8.4): StorePayoutAccountView responsibility.
package com.foodrescue.backend.finance.dto
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import jakarta.validation.constraints.*
import com.foodrescue.backend.finance.PayoutStatus
data class StorePayoutAccountView(val id: UUID, val storeId: UUID, val bankCode: String, val accountNumberMasked: String, val accountHolderName: String, val accountVersion: Int, val verifiedAt: Instant?, val effectiveFrom: Instant, val effectiveUntil: Instant?, val verificationNote: String?)
