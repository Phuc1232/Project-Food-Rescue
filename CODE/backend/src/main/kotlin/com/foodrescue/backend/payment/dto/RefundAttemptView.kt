// AC-02,04,06 (T6.5): RefundAttemptView responsibility.
package com.foodrescue.backend.payment.dto

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** One recorded transfer attempt. evidenceFileIds: download through GET /api/v1/files/{id}/content. */
data class RefundAttemptView(val id: UUID, val status: String, val amount: BigDecimal, val bankTransactionId: String?, val transferredAt: Instant?,
                             val verifiedAt: Instant?, val note: String?, val evidenceFileIds: List<UUID>, val recordedAt: Instant)
