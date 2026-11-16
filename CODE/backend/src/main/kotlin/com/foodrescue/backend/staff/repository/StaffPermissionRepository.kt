// AC-06,07 (T3.4): StaffPermissionRepository responsibility.
package com.foodrescue.backend.staff.repository

import com.foodrescue.backend.staff.entity.StaffPermission
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface StaffPermissionRepository : JpaRepository<StaffPermission, UUID> {
    fun findAllByAssignmentId(assignmentId: UUID): List<StaffPermission>

    @Modifying(flushAutomatically = true)
    @Query("delete from StaffPermission p where p.assignment.id = :assignmentId")
    fun deleteAllByAssignmentId(@Param("assignmentId") assignmentId: UUID)
}
