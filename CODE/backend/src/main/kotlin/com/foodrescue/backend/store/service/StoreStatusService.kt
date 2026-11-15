// AC-09,10,11,14: StoreStatusService responsibility.
package com.foodrescue.backend.store.service

import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.exception.authenticationFailed
import com.foodrescue.backend.store.StoreStatus
import com.foodrescue.backend.store.StoreStatusAction
import com.foodrescue.backend.store.dto.AdminStoreDetail
import com.foodrescue.backend.store.dto.StoreStatusRequest
import com.foodrescue.backend.store.repository.StoreRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/**
 * UC-77 status part: admin suspends (ACTIVE -> SUSPENDED) or reactivates (SUSPENDED -> ACTIVE) with a reason. Suspension
 * blocks batches without orders (BR-55); confirmed orders are T7.5. Owners have no path to change status (UC-41 E2).
 */
@Service
class StoreStatusService(
    private val stores: StoreRepository,
    private val accounts: AccountRepository,
    private val batches: SaleBatchBlocker,
    private val history: StoreHistoryRecorder,
    private val queries: StoreAdminQueryService,
    private val clock: Clock
) {
    /** The store row lock makes concurrent transitions sequential; the loser sees the new status and gets 409. */
    @Transactional
    fun change(adminId: UUID, storeId: UUID, request: StoreStatusRequest): AdminStoreDetail {
        val store = stores.findForUpdate(storeId) ?: throw ApiException(404, "NOT_FOUND", "Resource not found")
        if (store.owner.id == adminId) throw ApiException(403, "ACCESS_DENIED", "Access denied")
        val action = request.action!!
        val (from, to) = when (action) {
            StoreStatusAction.SUSPEND -> StoreStatus.ACTIVE to StoreStatus.SUSPENDED
            StoreStatusAction.REACTIVATE -> StoreStatus.SUSPENDED to StoreStatus.ACTIVE
        }
        if (store.status != from) throw ApiException(409, "CONFLICT", "Store status is ${store.status}, expected $from")
        val admin = accounts.findById(adminId).orElse(null) ?: authenticationFailed()
        val reason = request.reason!!.trim()
        val before = mapOf("status" to store.status.name, "statusReason" to store.statusReason)
        store.status = to
        store.statusReason = if (to == StoreStatus.SUSPENDED) reason else null
        store.statusChangedAt = clock.instant()
        stores.saveAndFlush(store)
        val blocked = if (to == StoreStatus.SUSPENDED) batches.blockBatchesWithoutOrders(store.id!!) else 0
        history.record(admin, store, StoreHistoryRecorder.STORE, store.id!!,
            if (to == StoreStatus.SUSPENDED) "STORE_SUSPENDED" else "STORE_REACTIVATED", before,
            mapOf("status" to to.name, "reason" to reason, "blockedSaleBatches" to blocked))
        return queries.detail(store)
    }
}
