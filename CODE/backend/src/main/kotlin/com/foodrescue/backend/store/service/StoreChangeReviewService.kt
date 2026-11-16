// AC-07: StoreChangeReviewService responsibility.
package com.foodrescue.backend.store.service

import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.exception.authenticationFailed
import com.foodrescue.backend.common.response.FieldViolation
import com.foodrescue.backend.common.response.PageResponse
import com.foodrescue.backend.common.validation.PageQuery
import com.foodrescue.backend.store.ChangeRequestDecision
import com.foodrescue.backend.store.StoreChangeRequestStatus
import com.foodrescue.backend.store.StoreStatus
import com.foodrescue.backend.store.dto.AdminChangeRequestView
import com.foodrescue.backend.store.dto.ChangeDecisionRequest
import com.foodrescue.backend.store.dto.StoreChangeValues
import com.foodrescue.backend.store.repository.StoreChangeRequestRepository
import com.foodrescue.backend.store.repository.StoreRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/** Admin side of change requests. Callers must already be ADMIN (checked at the controller). */
@Service
class StoreChangeReviewService(
    private val requests: StoreChangeRequestRepository,
    private val stores: StoreRepository,
    private val accounts: AccountRepository,
    private val history: StoreHistoryRecorder,
    private val views: StoreViews,
    private val clock: Clock
) {
    @Transactional(readOnly = true)
    fun list(status: StoreChangeRequestStatus?, page: Int, size: Int): PageResponse<AdminChangeRequestView> {
        val pageable = PageQuery().pageable(page, size, "createdAt")
        val result = if (status == null) requests.findAll(pageable) else requests.findAllByStatus(status, pageable)
        return PageResponse(result.content.map(views::adminRequest), page, size, result.totalElements, result.totalPages)
    }

    @Transactional(readOnly = true)
    fun detail(id: UUID): AdminChangeRequestView = views.adminRequest(requests.findById(id).orElse(null) ?: notFound())

    /** Locks the request, then the store, so decision vs cancel and two decisions resolve to one winner (T3.3 P5). */
    @Transactional
    fun decide(adminId: UUID, id: UUID, decision: ChangeDecisionRequest): AdminChangeRequestView {
        val request = requests.findForUpdate(id) ?: notFound()
        if (request.store.owner.id == adminId) throw ApiException(403, "ACCESS_DENIED", "Access denied")
        if (request.status != StoreChangeRequestStatus.PENDING) throw ApiException(409, "CONFLICT", "Change request has already been resolved")
        val reason = decision.reason?.trim()?.takeIf { it.isNotEmpty() }
        if (reason == null && decision.decision == ChangeRequestDecision.REJECT)
            throw ApiException(400, "VALIDATION_FAILED", "Invalid request", listOf(FieldViolation("reason", "Reason is required")))
        val admin = accounts.findById(adminId).orElse(null) ?: authenticationFailed()
        val store = stores.findForUpdate(request.store.id!!) ?: notFound()
        if (store.status == StoreStatus.PENDING_APPROVAL || store.status == StoreStatus.CLOSED)
            throw ApiException(409, "CONFLICT", "Store is not open for changes")

        var before: Map<String, Any?>? = null
        var after: Map<String, Any?> = mapOf("reason" to reason)
        if (decision.decision == ChangeRequestDecision.APPROVE) {
            val proposed = views.values(request.proposedValues)
            before = views.toMap(StoreChangeValues(store.name.takeIf { proposed.name != null }, store.address.takeIf { proposed.address != null },
                store.latitude.takeIf { proposed.latitude != null }, store.longitude.takeIf { proposed.longitude != null }))
            proposed.name?.let { store.name = it }; proposed.address?.let { store.address = it }
            proposed.latitude?.let { store.latitude = it }; proposed.longitude?.let { store.longitude = it }
            stores.saveAndFlush(store)
            after = views.toMap(proposed) + after
            request.status = StoreChangeRequestStatus.APPROVED
        } else request.status = StoreChangeRequestStatus.REJECTED
        request.resolvedBy = admin; request.resolvedAt = clock.instant(); request.resolutionReason = reason
        requests.saveAndFlush(request)
        history.record(admin, store, StoreHistoryRecorder.CHANGE_REQUEST, request.id!!,
            if (request.status == StoreChangeRequestStatus.APPROVED) "STORE_CHANGE_APPROVED" else "STORE_CHANGE_REJECTED", before, after)
        return views.adminRequest(request)
    }

    private fun notFound(): Nothing = throw ApiException(404, "NOT_FOUND", "Resource not found")
}
