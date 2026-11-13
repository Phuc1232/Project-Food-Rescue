// AC-07: AdminApplicationSummary responsibility.
package com.foodrescue.backend.store.dto

import java.time.Instant
import java.util.UUID

data class AdminApplicationSummary(
    val id: UUID, val storeId: UUID, val storeName: String, val applicant: AccountSummary, val revision: Int, val status: String, val submittedAt: Instant
)
