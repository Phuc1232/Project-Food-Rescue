// AC-01,04,06,08 (T4.4): BoxView responsibility.
package com.foodrescue.backend.surprisebox.dto

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** meetsBatchTerms: value >= batch minimum and safe until the end of pickup (BR-12, BR-18). */
data class BoxView(val id: UUID, val boxCode: String, val batchId: UUID, val status: String, val totalListedValue: BigDecimal,
                   val safeUntil: Instant, val packedAt: Instant, val packedById: UUID, val meetsBatchTerms: Boolean)
