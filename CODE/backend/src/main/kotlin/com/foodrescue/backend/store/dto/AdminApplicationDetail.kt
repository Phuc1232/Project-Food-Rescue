// AC-07,08,09,10: AdminApplicationDetail responsibility.
package com.foodrescue.backend.store.dto

import java.time.Instant
import java.util.UUID

data class AdminApplicationDetail(
    val id: UUID, val revision: Int, val status: String, val submittedAt: Instant, val reviewedAt: Instant?, val reviewReason: String?,
    val applicant: AccountSummary, val reviewer: AccountSummary?, val store: StoreView, val submission: SubmissionView,
    val documents: List<DocumentView>, val history: List<ApplicationHistoryItem>
)
