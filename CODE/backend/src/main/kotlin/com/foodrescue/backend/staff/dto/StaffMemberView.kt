// AC-01..07 (T3.4): StaffMemberView responsibility.
package com.foodrescue.backend.staff.dto

import com.foodrescue.backend.store.dto.AccountSummary
import java.time.Instant
import java.util.UUID

/** accountCreated is true only in the response that created a new account (setup link sent). */
data class StaffMemberView(
    val assignmentId: UUID, val account: AccountSummary, val status: String, val permissions: List<String>,
    val assignedAt: Instant, val revokedAt: Instant?, val accountCreated: Boolean = false
)
