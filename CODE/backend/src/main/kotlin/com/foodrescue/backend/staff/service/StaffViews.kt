// AC-01,03,09 (T3.4): StaffViews responsibility.
package com.foodrescue.backend.staff.service

import com.foodrescue.backend.staff.dto.MyAssignmentView
import com.foodrescue.backend.staff.dto.StaffMemberView
import com.foodrescue.backend.staff.entity.StaffAssignment
import com.foodrescue.backend.staff.repository.StaffPermissionRepository
import com.foodrescue.backend.store.dto.AccountSummary
import com.foodrescue.backend.store.dto.StoreSummary
import org.springframework.stereotype.Component

/** Must run inside a transaction (lazy associations). */
@Component
class StaffViews(private val permissions: StaffPermissionRepository) {
    fun codes(a: StaffAssignment): List<String> = permissions.findAllByAssignmentId(a.id!!).map { it.permission.code }.sorted()

    fun member(a: StaffAssignment, accountCreated: Boolean = false) = StaffMemberView(a.id!!,
        AccountSummary(a.account.id!!, a.account.loginIdentifier, a.account.displayName), a.status.name, codes(a), a.assignedAt,
        a.revokedAt, accountCreated)

    fun mine(a: StaffAssignment) = MyAssignmentView(a.id!!, StoreSummary(a.store.id!!, a.store.name, a.store.status.name),
        a.status.name, codes(a), a.assignedAt)
}
