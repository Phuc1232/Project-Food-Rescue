// AC-08,09,13: ApplicationHistoryItem responsibility.
package com.foodrescue.backend.store.dto

import java.time.Instant
import java.util.UUID

data class ApplicationHistoryItem(val id: UUID, val revision: Int, val status: String, val submittedAt: Instant, val reviewedAt: Instant?, val reviewReason: String?)
