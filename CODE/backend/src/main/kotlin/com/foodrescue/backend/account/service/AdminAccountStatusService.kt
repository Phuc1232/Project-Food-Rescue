// AC-03,04,05,06,07 (T3.5): AdminAccountStatusService responsibility.
package com.foodrescue.backend.account.service

import com.foodrescue.backend.account.AccountStatus
import com.foodrescue.backend.account.AccountStatusAction
import com.foodrescue.backend.account.dto.AccountStatusRequest
import com.foodrescue.backend.account.dto.AdminAccountDetail
import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.audit.AuditService
import com.foodrescue.backend.auth.service.SessionService
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.exception.authenticationFailed
import com.foodrescue.backend.common.response.FieldViolation
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.util.UUID

/**
 * UC-70. Locking takes effect on the next request (the JWT converter requires an ACTIVE account) and revokes every session.
 * Orders, money, stores, roles and staff assignments are never changed here (UC-70 A1, postconditions).
 */
@Service
class AdminAccountStatusService(
    private val accounts: AccountRepository,
    private val sessions: SessionService,
    private val outstanding: OutstandingWorkCounter,
    private val queries: AdminAccountQueryService,
    private val audit: AuditService,
    private val mapper: ObjectMapper,
    private val clock: Clock
) {
    @Transactional
    fun change(adminId: UUID, accountId: UUID, request: AccountStatusRequest): AdminAccountDetail {
        if (adminId == accountId) throw ApiException(403, "ACCESS_DENIED", "Administrators cannot change their own status")
        val target = accounts.findForUpdate(accountId) ?: throw ApiException(404, "NOT_FOUND", "Resource not found")
        val admin = accounts.findById(adminId).orElse(null) ?: authenticationFailed()
        val (from, to) = when (request.action!!) {
            AccountStatusAction.LOCK -> AccountStatus.ACTIVE to AccountStatus.LOCKED
            AccountStatusAction.UNLOCK -> AccountStatus.LOCKED to AccountStatus.ACTIVE
        }
        if (target.status != from) throw ApiException(409, "CONFLICT", "Account status is ${target.status}, expected $from")
        val plan = request.outstandingPlan?.trim()?.takeIf { it.isNotEmpty() }
        if (to == AccountStatus.LOCKED) {
            val customer = outstanding.customerOrders(accountId); val store = outstanding.storeOrders(accountId)
            if ((customer > 0 || store > 0) && plan == null)
                throw ApiException(409, "OUTSTANDING_WORK", "Account has unfinished orders; provide outstandingPlan",
                    listOf(FieldViolation("customerOrders", customer.toString()), FieldViolation("storeOrders", store.toString())))
        }
        val before = mapOf("status" to target.status.name, "statusReason" to target.statusReason)
        target.status = to
        target.statusReason = if (to == AccountStatus.LOCKED) request.reason!!.trim() else null
        target.statusChangedAt = clock.instant()
        accounts.saveAndFlush(target)
        if (to == AccountStatus.LOCKED) sessions.revokeAll(target, "SESSIONS_REVOKED_BY_ADMIN")
        audit.record(admin, AdminAccountQueryService.ENTITY, target.id!!, if (to == AccountStatus.LOCKED) "ACCOUNT_LOCKED" else "ACCOUNT_UNLOCKED",
            afterState = mapper.writeValueAsString(mapOf("status" to to.name, "reason" to request.reason!!.trim(), "outstandingPlan" to plan)),
            beforeState = mapper.writeValueAsString(before))
        return queries.detail(target)
    }
}
