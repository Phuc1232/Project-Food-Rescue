// AC-01..12 (T8.4): AdminPayoutRecipientView responsibility.
package com.foodrescue.backend.finance.dto
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import jakarta.validation.constraints.*
import com.foodrescue.backend.finance.PayoutStatus
data class AdminPayoutRecipientView(val id: UUID, val storeId: UUID, val bankCode: String, val accountNumber: String, val accountHolderName: String, val accountVersion: Int, val verifiedAt: Instant?)
