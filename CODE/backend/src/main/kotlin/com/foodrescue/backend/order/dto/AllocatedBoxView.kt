// AC-06 (T5.3): AllocatedBoxView responsibility.
package com.foodrescue.backend.order.dto

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class AllocatedBoxView(val boxId: UUID, val boxCode: String, val totalListedValue: BigDecimal, val boxStatus: String, val allocationStatus: String,
                            val reservedAt: Instant, val releasedAt: Instant?, val releaseReason: String?)
