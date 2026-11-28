// AC-01..07 (T8.5): AdminAuditLogItem responsibility.
package com.foodrescue.backend.audit.dto
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import com.foodrescue.backend.common.response.PageResponse
import tools.jackson.databind.JsonNode
data class AdminAuditLogItem(val id: UUID, val storeId: UUID?, val storeName: String?, val entityType: String, val entityId: UUID, val action: String, val actorId: UUID?, val actorLoginIdentifier: String?, val actorDisplayName: String?, val actorRole: String, val correlationId: String?, val occurredAt: Instant, val before: JsonNode?, val after: JsonNode?)
