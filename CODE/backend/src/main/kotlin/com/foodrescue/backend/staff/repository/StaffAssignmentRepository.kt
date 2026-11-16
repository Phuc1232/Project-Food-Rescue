// AC-01..09 (T3.4): StaffAssignmentRepository responsibility.
package com.foodrescue.backend.staff.repository

import com.foodrescue.backend.staff.AssignmentStatus
import com.foodrescue.backend.staff.entity.StaffAssignment
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface StaffAssignmentRepository : JpaRepository<StaffAssignment, UUID> {
    fun findAllByStoreIdOrderByAssignedAtDesc(storeId: UUID): List<StaffAssignment>
    fun findAllByAccountIdAndStatusInOrderByAssignedAtDesc(accountId: UUID, statuses: Collection<AssignmentStatus>): List<StaffAssignment>
    fun findByStoreIdAndAccountId(storeId: UUID, accountId: UUID): StaffAssignment?
    fun countByStoreIdAndStatusIn(storeId: UUID, statuses: Collection<AssignmentStatus>): Long

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from StaffAssignment a where a.id = :id")
    fun findForUpdate(@Param("id") id: UUID): StaffAssignment?
}
