// AC-01..10 (T8.3): LedgerEntryView responsibility.
package com.foodrescue.backend.finance.dto
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import com.foodrescue.backend.common.response.PageResponse
data class LedgerEntryView(val id: UUID, val eventId: UUID, val eventType: String, val type: String, val amount: BigDecimal, val currency: String, val originalEntryId: UUID?, val businessKey: String?, val occurredAt: Instant)
