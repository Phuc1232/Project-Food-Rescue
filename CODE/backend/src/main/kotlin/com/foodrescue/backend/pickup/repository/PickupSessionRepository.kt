package com.foodrescue.backend.pickup.repository

import com.foodrescue.backend.pickup.entity.PickupSession
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PickupSessionRepository : JpaRepository<PickupSession, UUID>
