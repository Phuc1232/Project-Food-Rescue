// AC-01..10 (T8.3): StoreEntitlementItemView responsibility.
package com.foodrescue.backend.finance.dto
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import com.foodrescue.backend.common.response.PageResponse
data class StoreEntitlementItemView(val ledgerEntryId: UUID, val orderId: UUID, val orderCode: String, val type: String, val grossAmount: BigDecimal, val adjustmentAmount: BigDecimal, val netAmount: BigDecimal, val eligibilityStatus: String, val holdReason: String?, val occurredAt: Instant)
