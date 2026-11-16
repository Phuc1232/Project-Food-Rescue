// AC-01,02,03,04,08,12,13: StoreProfileService responsibility.
package com.foodrescue.backend.store.service

import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.audit.repository.AuditLogRepository
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.exception.authenticationFailed
import com.foodrescue.backend.common.response.PageResponse
import com.foodrescue.backend.common.security.StoreAuthorizationService
import com.foodrescue.backend.common.validation.PageQuery
import com.foodrescue.backend.store.dto.OpeningHourView
import com.foodrescue.backend.store.dto.StoreHistoryItem
import com.foodrescue.backend.store.dto.StoreProfileView
import com.foodrescue.backend.store.dto.StorePublicView
import com.foodrescue.backend.store.entity.Store
import com.foodrescue.backend.store.entity.StoreOpeningHour
import com.foodrescue.backend.store.repository.StoreOpeningHourRepository
import com.foodrescue.backend.store.repository.StoreRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** Owner side of UC-40/UC-41 for fields applied without review, plus the customer-facing store view. */
@Service
class StoreProfileService(
    private val authorization: StoreAuthorizationService,
    private val stores: StoreRepository,
    private val hours: StoreOpeningHourRepository,
    private val accounts: AccountRepository,
    private val audits: AuditLogRepository,
    private val parser: StoreProfileUpdateParser,
    private val hoursValidator: OpeningHoursValidator,
    private val salesPolicy: StoreSalesPolicy,
    private val history: StoreHistoryRecorder,
    private val views: StoreViews,
    private val entityManager: EntityManager
) {
    @Transactional(readOnly = true)
    fun view(accountId: UUID, storeId: UUID): StoreProfileView {
        authorize(accountId, storeId)
        return views.profile(stores.findById(storeId).orElse(null) ?: notFound())
    }

    /** Version check under a row lock; a change of shifts alone force-increments the version (T3.3 P1). */
    @Transactional
    fun update(accountId: UUID, storeId: UUID, body: Map<String, Any?>?): StoreProfileView {
        authorize(accountId, storeId)
        val command = parser.parse(body)
        val shifts = command.openingHours?.let { requested ->
            val (valid, errors) = hoursValidator.validate(requested)
            if (errors.isNotEmpty()) throw ApiException(400, "VALIDATION_FAILED", "Invalid request", errors)
            valid
        }
        val store = stores.findForUpdate(storeId) ?: notFound()
        if (store.version != command.version) throw ApiException(409, "CONFLICT", "Store has changed; reload and try again")

        val before = linkedMapOf<String, Any?>(); val after = linkedMapOf<String, Any?>()
        if (command.descriptionPresent && command.description != store.description) {
            before[DESCRIPTION] = store.description; after[DESCRIPTION] = command.description
            store.description = command.description
        }
        if (shifts != null) {
            val current = views.openingHours(store)
            val next = shifts.map { OpeningHourView(it.dayOfWeek, it.shiftNumber, it.opensAt.format(PartnerApplicationViews.HH_MM),
                it.closesAt.format(PartnerApplicationViews.HH_MM), it.closesNextDay) }
            if (current != next) {
                hours.deleteAllByStoreId(store.id!!)
                hours.saveAllAndFlush(shifts.map { StoreOpeningHour(store, it.dayOfWeek, it.shiftNumber, it.opensAt, it.closesAt, it.closesNextDay) })
                before[OPENING_HOURS] = current; after[OPENING_HOURS] = next
            }
        }
        if (after.isEmpty()) return views.profile(store)
        if (DESCRIPTION !in after) entityManager.lock(store, LockModeType.PESSIMISTIC_FORCE_INCREMENT)
        stores.saveAndFlush(store)
        val actor = accounts.findById(accountId).orElse(null) ?: authenticationFailed()
        history.record(actor, store, StoreHistoryRecorder.STORE, store.id!!, "STORE_PROFILE_UPDATED", before, after)
        return views.profile(store)
    }

    @Transactional(readOnly = true)
    fun history(accountId: UUID, storeId: UUID, page: Int, size: Int): PageResponse<StoreHistoryItem> {
        authorize(accountId, storeId)
        val store = stores.findById(storeId).orElse(null) ?: notFound()
        return historyPage(store, page, size, withActorId = false)
    }

    fun historyPage(store: Store, page: Int, size: Int, withActorId: Boolean): PageResponse<StoreHistoryItem> {
        val result = audits.findAllByStoreIdAndEntityTypeIn(store.id!!, StoreHistoryRecorder.ENTITY_TYPES,
            PageQuery().pageable(page, size, "createdAt"))
        return PageResponse(result.content.map { views.history(it, store, withActorId) }, page, size, result.totalElements, result.totalPages)
    }

    /** Customers (any signed-in account, D10) only see sellable stores; everything else looks absent (AC-12). */
    @Transactional(readOnly = true)
    fun publicView(storeId: UUID): StorePublicView {
        val store = stores.findById(storeId).orElse(null)?.takeIf(salesPolicy::isSellable) ?: notFound()
        return views.public(store)
    }

    /** Owner only: staff never hold this code (T3.3 Q11); stores awaiting approval or closed give no context. */
    fun authorize(accountId: UUID, storeId: UUID) = authorization.requirePermission(accountId, storeId, PERMISSION)

    private fun notFound(): Nothing = throw ApiException(404, "NOT_FOUND", "Resource not found")

    companion object {
        const val PERMISSION = "STORE_PROFILE_MANAGE"
        private const val DESCRIPTION = "description"
        private const val OPENING_HOURS = "openingHours"
    }
}
