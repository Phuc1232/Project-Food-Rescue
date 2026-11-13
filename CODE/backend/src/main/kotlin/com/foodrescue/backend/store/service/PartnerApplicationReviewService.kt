// AC-06,07,08,09,10,11,12,14: PartnerApplicationReviewService responsibility.
package com.foodrescue.backend.store.service

import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.audit.AuditService
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.exception.authenticationFailed
import com.foodrescue.backend.common.response.FieldViolation
import com.foodrescue.backend.common.response.PageResponse
import com.foodrescue.backend.common.validation.PageQuery
import com.foodrescue.backend.store.ApplicationDecision
import com.foodrescue.backend.store.ApplicationStatus
import com.foodrescue.backend.store.StoreStatus
import com.foodrescue.backend.store.dto.AdminApplicationDetail
import com.foodrescue.backend.store.dto.AdminApplicationSummary
import com.foodrescue.backend.store.dto.DecisionRequest
import com.foodrescue.backend.store.repository.PartnerApplicationRepository
import com.foodrescue.backend.store.repository.StoreRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/** Admin side of UC-66/UC-67. Callers must already be ADMIN (checked at the controller). */
@Service
class PartnerApplicationReviewService(
    private val applications: PartnerApplicationRepository,
    private val stores: StoreRepository,
    private val accounts: AccountRepository,
    private val views: PartnerApplicationViews,
    private val audit: AuditService,
    private val clock: Clock
) {
    @Transactional(readOnly = true)
    fun list(status: ApplicationStatus?, page: Int, size: Int): PageResponse<AdminApplicationSummary> {
        val pageable = PageQuery().pageable(page, size, "createdAt")
        val result = if (status == null) applications.findAll(pageable) else applications.findAllByStatus(status, pageable)
        return PageResponse(result.content.map(views::summary), page, size, result.totalElements, result.totalPages)
    }

    @Transactional(readOnly = true)
    fun detail(id: UUID): AdminApplicationDetail = views.detail(applications.findById(id).orElse(null) ?: notFound())

    /** Row locks (application, then store) make concurrent decisions resolve to exactly one winner (UC-67 E1). */
    @Transactional
    fun decide(adminId: UUID, id: UUID, request: DecisionRequest): AdminApplicationDetail {
        val application = applications.findForUpdate(id) ?: notFound()
        if (application.applicant.id == adminId) throw ApiException(403, "ACCESS_DENIED", "Access denied")
        if (application.status != ApplicationStatus.SUBMITTED)
            throw ApiException(409, "CONFLICT", "Application has already been decided")
        val decision = request.decision!!
        val reason = request.reason?.trim()?.takeIf { it.isNotEmpty() }
        if (reason == null && decision != ApplicationDecision.APPROVE)
            throw ApiException(400, "VALIDATION_FAILED", "Invalid request", listOf(FieldViolation("reason", "Reason is required")))
        val admin = accounts.findById(adminId).orElse(null) ?: authenticationFailed()
        val store = stores.findForUpdate(application.store.id!!) ?: notFound()
        application.status = when (decision) {
            ApplicationDecision.APPROVE -> {
                if (store.status != StoreStatus.PENDING_APPROVAL || stores.existsOtherOpenStore(application.applicant.id!!, store.id!!))
                    throw ApiException(409, "CONFLICT", "Store or ownership has changed; reload the application")
                store.status = StoreStatus.ACTIVE
                ApplicationStatus.APPROVED
            }
            ApplicationDecision.REJECT -> ApplicationStatus.REJECTED
            ApplicationDecision.REQUEST_INFO -> ApplicationStatus.NEEDS_INFO
        }
        application.reviewer = admin; application.reviewedAt = clock.instant(); application.reviewReason = reason
        applications.saveAndFlush(application)
        audit.record(admin, PartnerApplicationService.ENTITY, application.id!!, ACTIONS.getValue(decision), store,
            """{"revision":${application.revision},"status":"${application.status}"}""")
        return views.detail(application)
    }

    private fun notFound(): Nothing = throw ApiException(404, "NOT_FOUND", "Resource not found")

    private companion object {
        val ACTIONS = mapOf(ApplicationDecision.APPROVE to "PARTNER_APPLICATION_APPROVED",
            ApplicationDecision.REJECT to "PARTNER_APPLICATION_REJECTED", ApplicationDecision.REQUEST_INFO to "PARTNER_APPLICATION_INFO_REQUESTED")
    }
}
