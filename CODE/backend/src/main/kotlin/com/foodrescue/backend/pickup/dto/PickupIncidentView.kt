// AC-01..14 (T7.2): PickupIncidentView responsibility.
package com.foodrescue.backend.pickup.dto

import java.time.Instant
import java.util.UUID

data class PickupIncidentView(val id: UUID, val orderId: UUID, val sessionId: UUID?, val actorId: UUID,
    val reason: String, val customerArrived: Boolean, val occurredAt: Instant, val resolution: String?)
