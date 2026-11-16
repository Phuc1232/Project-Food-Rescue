// AC-01 (T3.5): AdminAccountSummary responsibility.
package com.foodrescue.backend.account.dto

import java.time.Instant
import java.util.UUID

/** Minimal data for administration (UC-68); never contains credentials. */
data class AdminAccountSummary(val id: UUID, val email: String, val displayName: String, val roles: List<String>, val status: String,
                               val emailVerified: Boolean, val createdAt: Instant)
