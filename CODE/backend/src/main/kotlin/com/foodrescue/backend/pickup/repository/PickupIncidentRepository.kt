package com.foodrescue.backend.pickup.repository

import com.foodrescue.backend.pickup.entity.PickupIncident
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PickupIncidentRepository : JpaRepository<PickupIncident, UUID>
