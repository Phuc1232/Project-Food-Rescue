// AC-15: AdminStoreSummary responsibility.
package com.foodrescue.backend.store.dto

import java.time.Instant
import java.util.UUID

data class AdminStoreSummary(val id: UUID, val name: String, val status: String, val owner: AccountSummary,
                             val statusChangedAt: Instant?, val createdAt: Instant)
