package com.foodrescue.backend.staff.repository

import com.foodrescue.backend.staff.entity.StaffPermission
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface StaffPermissionRepository : JpaRepository<StaffPermission, UUID>
