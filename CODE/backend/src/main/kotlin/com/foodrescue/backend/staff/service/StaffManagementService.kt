// AC-01,02,03,04,05,06,07,08 (T3.4): StaffManagementService responsibility.
package com.foodrescue.backend.staff.service

import com.foodrescue.backend.account.AccountStatus
import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.auth.EmailIdentifierNormalizer
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.exception.authenticationFailed
import com.foodrescue.backend.common.response.FieldViolation
import com.foodrescue.backend.common.security.StoreAuthorizationService
import com.foodrescue.backend.staff.AssignmentStatus
import com.foodrescue.backend.staff.dto.StaffInviteRequest
import com.foodrescue.backend.staff.dto.StaffMemberView
import com.foodrescue.backend.staff.entity.Permission
import com.foodrescue.backend.staff.entity.StaffAssignment
import com.foodrescue.backend.staff.entity.StaffPermission
import com.foodrescue.backend.staff.repository.StaffAssignmentRepository
import com.foodrescue.backend.staff.repository.StaffPermissionRepository
import com.foodrescue.backend.store.repository.StoreRepository
import com.foodrescue.backend.store.service.StoreHistoryRecorder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/** Owner side of UC-43..46. Every method re-checks the caller owns the store (STAFF_MANAGE is never granted to staff). */
@Service
class StaffManagementService(
    private val authorization: StoreAuthorizationService,
    private val stores: StoreRepository,
    private val accounts: AccountRepository,
    private val assignments: StaffAssignmentRepository,
    private val grants: StaffPermissionRepository,
    private val catalog: StaffPermissionCatalog,
    private val provisioner: StaffAccountProvisioner,
    private val normalizer: EmailIdentifierNormalizer,
    private val history: StoreHistoryRecorder,
    private val views: StaffViews,
    private val clock: Clock
) {
    @Transactional(readOnly = true)
    fun list(ownerId: UUID, storeId: UUID, status: AssignmentStatus?): List<StaffMemberView> {
        authorize(ownerId, storeId)
        return assignments.findAllByStoreIdOrderByAssignedAtDesc(storeId).filter { status == null || it.status == status }.map { views.member(it) }
    }

    /** Store row lock serializes invitations per store (T3.4 P1). */
    @Transactional
    fun invite(ownerId: UUID, storeId: UUID, request: StaffInviteRequest): StaffMemberView {
        authorize(ownerId, storeId)
        val permissions = catalog.resolve(request.permissions!!)
        val email = try { normalizer.normalize(request.email!!) } catch (_: Exception) { invalid("email", "Invalid email") }
        val store = stores.findForUpdate(storeId) ?: notFound()
        val owner = accounts.findById(ownerId).orElse(null) ?: authenticationFailed()
        if (email == store.owner.loginIdentifier) invalid("email", "The store owner cannot be staff of the same store")
        if (assignments.countByStoreIdAndStatusIn(storeId, OPEN) >= MAX_OPEN)
            throw ApiException(409, "CONFLICT", "Too many staff members or pending invitations")

        val existing = accounts.findEmailForUpdate(email)
        val created = existing == null
        val account = existing ?: run {
            val name = request.displayName?.trim()?.takeIf { it.isNotEmpty() } ?: invalid("displayName", "Required for a new account")
            provisioner.create(email, name)
        }
        if (!created && (account.status != AccountStatus.ACTIVE || account.emailVerifiedAt == null))
            throw ApiException(409, "CONFLICT", "This account cannot be added as staff")
        // New accounts prove the email through the setup link; existing accounts must accept (UC-44 A1).
        val status = if (created) AssignmentStatus.ACTIVE else AssignmentStatus.INVITED
        val previous = assignments.findByStoreIdAndAccountId(storeId, account.id!!)
        if (previous != null && previous.status in OPEN) throw ApiException(409, "CONFLICT", "Already staff or invited")
        val assignment = previous?.apply { this.status = status; assignedAt = clock.instant(); revokedAt = null }
            ?: StaffAssignment(account, store, status, clock.instant())
        assignments.saveAndFlush(assignment)
        grants.deleteAllByAssignmentId(assignment.id!!)
        grant(assignment, permissions, owner)
        history.record(owner, store, ENTITY, assignment.id!!, if (created) "STAFF_ACCOUNT_CREATED" else "STAFF_INVITED", null,
            mapOf("accountId" to account.id, "status" to status.name, "permissions" to permissions.map { it.code }))
        return views.member(assignment, accountCreated = created)
    }

    @Transactional
    fun changePermissions(ownerId: UUID, storeId: UUID, assignmentId: UUID, codes: List<String>): StaffMemberView {
        authorize(ownerId, storeId)
        val permissions = catalog.resolve(codes)
        val assignment = lockedAssignment(storeId, assignmentId)
        if (assignment.status !in OPEN) throw ApiException(409, "CONFLICT", "Staff member is no longer in this store")
        val before = views.codes(assignment)
        if (before == permissions.map { it.code }) return views.member(assignment)
        val owner = accounts.findById(ownerId).orElse(null) ?: authenticationFailed()
        grants.deleteAllByAssignmentId(assignment.id!!)
        grant(assignment, permissions, owner)
        history.record(owner, assignment.store, ENTITY, assignment.id!!, "STAFF_PERMISSIONS_CHANGED",
            mapOf("permissions" to before), mapOf("permissions" to permissions.map { it.code }))
        return views.member(assignment)
    }

    /** Takes effect on the staff member's next request: authorization reads ACTIVE assignments from the database. */
    @Transactional
    fun revoke(ownerId: UUID, storeId: UUID, assignmentId: UUID, reason: String): StaffMemberView {
        authorize(ownerId, storeId)
        val assignment = lockedAssignment(storeId, assignmentId)
        if (assignment.status == AssignmentStatus.REVOKED) return views.member(assignment) // UC-46 A1
        if (assignment.status == AssignmentStatus.DECLINED) throw ApiException(409, "CONFLICT", "Invitation was declined")
        val owner = accounts.findById(ownerId).orElse(null) ?: authenticationFailed()
        val before = mapOf("status" to assignment.status.name, "permissions" to views.codes(assignment))
        grants.deleteAllByAssignmentId(assignment.id!!)
        assignment.status = AssignmentStatus.REVOKED; assignment.revokedAt = clock.instant()
        assignments.saveAndFlush(assignment)
        history.record(owner, assignment.store, ENTITY, assignment.id!!, "STAFF_REVOKED", before,
            mapOf("status" to assignment.status.name, "reason" to reason.trim()))
        return views.member(assignment)
    }

    private fun grant(assignment: StaffAssignment, permissions: List<Permission>, by: Account) {
        grants.saveAllAndFlush(permissions.map { StaffPermission(assignment, it, by, clock.instant()) })
    }

    private fun lockedAssignment(storeId: UUID, id: UUID) = assignments.findForUpdate(id)?.takeIf { it.store.id == storeId } ?: notFound()

    private fun authorize(accountId: UUID, storeId: UUID) = authorization.requirePermission(accountId, storeId, PERMISSION)

    private fun invalid(field: String, message: String): Nothing =
        throw ApiException(400, "VALIDATION_FAILED", "Invalid request", listOf(FieldViolation(field, message)))

    private fun notFound(): Nothing = throw ApiException(404, "NOT_FOUND", "Resource not found")

    companion object {
        const val ENTITY = "STAFF_ASSIGNMENT"
        const val PERMISSION = "STAFF_MANAGE"
        const val MAX_OPEN = 50L
        val OPEN = setOf(AssignmentStatus.INVITED, AssignmentStatus.ACTIVE)
    }
}
