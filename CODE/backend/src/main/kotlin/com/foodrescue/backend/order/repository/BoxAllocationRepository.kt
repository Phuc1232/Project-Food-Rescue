package com.foodrescue.backend.order.repository

import com.foodrescue.backend.order.entity.BoxAllocation
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BoxAllocationRepository : JpaRepository<BoxAllocation, UUID>
