// AC-02 (T3.5): AccountHistoryItem responsibility.
package com.foodrescue.backend.account.dto

import tools.jackson.databind.JsonNode
import java.time.Instant
import java.util.UUID

data class AccountHistoryItem(val action: String, val occurredAt: Instant, val actorId: UUID?, val before: JsonNode?, val after: JsonNode?)
