// AC-01..14 (T7.2): PickupSessionView responsibility.
package com.foodrescue.backend.pickup.dto

import java.time.Instant
import java.util.UUID

data class PickupSessionView(val id: UUID, val orderId: UUID, val orderCode: String, val actorId: UUID,
    val status: String, val startedAt: Instant, val completedAt: Instant?, val idempotencyKey: String)
