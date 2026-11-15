// AC-03,09 (T3.4): StaffSelfService responsibility.
package com.foodrescue.backend.staff.service

import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.exception.authenticationFailed
import com.foodrescue.backend.staff.AssignmentStatus
import com.foodrescue.backend.staff.dto.MyAssignmentView
import com.foodrescue.backend.staff.repository.StaffAssignmentRepository
import com.foodrescue.backend.store.service.StoreHistoryRecorder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** The invited/staff account's own view: pending invitations and stores it works for. */
@Service
class StaffSelfService(
    private val assignments: StaffAssignmentRepository,
    private val accounts: AccountRepository,
    private val history: StoreHistoryRecorder,
    private val views: StaffViews
) {
    @Transactional(readOnly = true)
    fun mine(accountId: UUID): List<MyAssignmentView> =
        assignments.findAllByAccountIdAndStatusInOrderByAssignedAtDesc(accountId, StaffManagementService.OPEN).map(views::mine)

    @Transactional
    fun respond(accountId: UUID, assignmentId: UUID, accept: Boolean): MyAssignmentView {
        val assignment = assignments.findForUpdate(assignmentId)?.takeIf { it.account.id == accountId }
            ?: throw ApiException(404, "NOT_FOUND", "Resource not found")
        if (assignment.status != AssignmentStatus.INVITED) throw ApiException(409, "CONFLICT", "Invitation is no longer pending")
        assignment.status = if (accept) AssignmentStatus.ACTIVE else AssignmentStatus.DECLINED
        assignments.saveAndFlush(assignment)
        val actor = accounts.findById(accountId).orElse(null) ?: authenticationFailed()
        history.record(actor, assignment.store, StaffManagementService.ENTITY, assignment.id!!,
            if (accept) "STAFF_INVITATION_ACCEPTED" else "STAFF_INVITATION_DECLINED", null, mapOf("status" to assignment.status.name))
        return views.mine(assignment)
    }
}
