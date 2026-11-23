package com.foodrescue.backend.pickup.repository

import com.foodrescue.backend.pickup.entity.PickupSession
import com.foodrescue.backend.pickup.PickupStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PickupSessionRepository : JpaRepository<PickupSession, UUID> {
    fun findByOrderIdAndStatusIn(orderId: UUID, statuses: Collection<PickupStatus>): PickupSession?
    fun findByIdempotencyKey(idempotencyKey: String): PickupSession?
}
