// AC-04,06,10 (T3.4): PermissionRepository responsibility.
package com.foodrescue.backend.staff.repository

import com.foodrescue.backend.staff.entity.Permission
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PermissionRepository : JpaRepository<Permission, UUID> {
    fun findAllByCodeIn(codes: Collection<String>): List<Permission>
}
