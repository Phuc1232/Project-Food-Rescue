// AC-01,13: ApplicationView responsibility.
package com.foodrescue.backend.store.dto

import java.time.Instant
import java.util.UUID

data class ApplicationView(
    val id: UUID, val revision: Int, val status: String, val submittedAt: Instant, val reviewedAt: Instant?, val reviewReason: String?,
    val submission: SubmissionView, val documents: List<DocumentView>
)
