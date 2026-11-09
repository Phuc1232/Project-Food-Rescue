package com.foodrescue.backend.pickup.repository

import com.foodrescue.backend.pickup.entity.NoShowCase
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface NoShowCaseRepository : JpaRepository<NoShowCase, UUID>
