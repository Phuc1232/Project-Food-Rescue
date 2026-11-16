// AC-03,04,05,08 (T4.3): SaleBatchLifecycleService responsibility.
package com.foodrescue.backend.surprisebox.service

import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.exception.authenticationFailed
import com.foodrescue.backend.store.service.StoreHistoryRecorder
import com.foodrescue.backend.store.service.StoreSalesPolicy
import com.foodrescue.backend.surprisebox.SaleBatchAction
import com.foodrescue.backend.surprisebox.SaleBatchStatus
import com.foodrescue.backend.surprisebox.SaleBatchStatus.*
import com.foodrescue.backend.surprisebox.dto.SaleBatchView
import com.foodrescue.backend.surprisebox.entity.SaleBatch
import com.foodrescue.backend.surprisebox.repository.SaleBatchRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/** UC-61 operations and the admin block. The batch row lock serializes transitions; a wrong source status is 409. */
@Service
class SaleBatchLifecycleService(
    private val access: SaleBatchAccess,
    private val batches: SaleBatchRepository,
    private val accounts: AccountRepository,
    private val salesPolicy: StoreSalesPolicy,
    private val availability: BatchAvailability,
    private val history: StoreHistoryRecorder,
    private val views: SaleBatchViews,
    private val clock: Clock
) {
    @Transactional
    fun apply(ownerId: UUID, storeId: UUID, batchId: UUID, action: SaleBatchAction): SaleBatchView {
        access.requireWrite(ownerId, storeId)
        val b = batches.findForUpdate(batchId)?.takeIf { it.store.id == storeId } ?: throw ApiException(404, "NOT_FOUND", "Resource not found")
        val (from, to) = when (action) {
            SaleBatchAction.PUBLISH -> setOf(DRAFT) to OPEN
            SaleBatchAction.PAUSE -> setOf(OPEN) to PAUSED
            SaleBatchAction.RESUME -> setOf(PAUSED) to OPEN
            SaleBatchAction.END -> setOf(DRAFT, OPEN, PAUSED) to ENDED
        }
        if (b.status !in from) throw ApiException(409, "CONFLICT", "Cannot $action a ${b.status} batch")
        if (to == OPEN) requireSellable(b)
        val before = b.status
        b.status = to
        batches.saveAndFlush(b)
        val owner = accounts.findById(ownerId).orElse(null) ?: authenticationFailed()
        history.record(owner, b.store, SaleBatchService.ENTITY, b.id!!, "SALE_BATCH_" + PAST.getValue(action), mapOf("status" to before.name),
            mapOf("status" to to.name))
        return views.view(b)
    }

    /** Admin stop of a batch (card: admin BLOCKED). Final: a blocked batch is never reopened. */
    @Transactional
    fun block(adminId: UUID, batchId: UUID, reason: String): SaleBatchView {
        val b = batches.findForUpdate(batchId) ?: throw ApiException(404, "NOT_FOUND", "Resource not found")
        if (b.status == ENDED || b.status == BLOCKED) throw ApiException(409, "CONFLICT", "Batch is already ${b.status}")
        val before = b.status
        b.status = BLOCKED; b.statusReason = reason.trim()
        batches.saveAndFlush(b)
        val admin = accounts.findById(adminId).orElse(null) ?: authenticationFailed()
        history.record(admin, b.store, SaleBatchService.ENTITY, b.id!!, "SALE_BATCH_BLOCKED", mapOf("status" to before.name),
            mapOf("status" to BLOCKED.name, "reason" to b.statusReason))
        return views.view(b)
    }

    /** SD-03 #16-22: store ACTIVE, template usable, deadline not passed, at least one sellable box. */
    private fun requireSellable(b: SaleBatch) {
        salesPolicy.requireSellable(b.store)
        if (!b.template.active) throw ApiException(409, "CONFLICT", "Template is inactive")
        if (!b.orderDeadline.isAfter(clock.instant())) throw ApiException(409, "ORDER_DEADLINE_PASSED", "Order deadline has passed")
        if (availability.available(b.id!!) == 0L)
            throw ApiException(409, "NO_ELIGIBLE_BOX", "No packed box meets the batch conditions yet; pack boxes first")
    }

    private companion object {
        val PAST = mapOf(SaleBatchAction.PUBLISH to "PUBLISHED", SaleBatchAction.PAUSE to "PAUSED", SaleBatchAction.RESUME to "RESUMED",
            SaleBatchAction.END to "ENDED")
    }
}
