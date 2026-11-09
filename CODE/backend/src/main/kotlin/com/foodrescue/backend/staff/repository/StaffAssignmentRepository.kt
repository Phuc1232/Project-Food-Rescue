package com.foodrescue.backend.staff.repository

import com.foodrescue.backend.staff.entity.StaffAssignment
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface StaffAssignmentRepository : JpaRepository<StaffAssignment, UUID>
