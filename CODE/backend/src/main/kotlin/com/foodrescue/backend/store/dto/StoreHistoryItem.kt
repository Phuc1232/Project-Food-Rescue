// AC-13: StoreHistoryItem responsibility.
package com.foodrescue.backend.store.dto

import com.fasterxml.jackson.annotation.JsonInclude
import tools.jackson.databind.JsonNode
import java.time.Instant
import java.util.UUID

/** One audited store change; actorId is only filled for administrators. */
data class StoreHistoryItem(
    val action: String, val occurredAt: Instant, val actorRole: String,
    @field:JsonInclude(JsonInclude.Include.NON_NULL) val actorId: UUID?,
    val before: JsonNode?, val after: JsonNode?
)
