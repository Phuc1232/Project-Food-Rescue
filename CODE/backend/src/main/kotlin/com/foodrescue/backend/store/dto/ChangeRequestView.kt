// AC-01,05,06,07: ChangeRequestView responsibility.
package com.foodrescue.backend.store.dto

import java.time.Instant
import java.util.UUID

/** current = store values when requested, proposed = requested values (null fields are not part of the change). */
data class ChangeRequestView(
    val id: UUID, val status: String, val current: StoreChangeValues, val proposed: StoreChangeValues, val note: String?,
    val documents: List<DocumentView>, val requestedAt: Instant, val resolvedAt: Instant?, val resolutionReason: String?
)
