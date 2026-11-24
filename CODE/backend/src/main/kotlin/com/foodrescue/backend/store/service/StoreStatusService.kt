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
    private val clock: Clock,
    private val orders: com.foodrescue.backend.order.repository.CustomerOrderRepository,
    private val saleBatches: com.foodrescue.backend.surprisebox.repository.SaleBatchRepository,
    private val allocations: com.foodrescue.backend.order.repository.BoxAllocationRepository,
    private val boxes: com.foodrescue.backend.surprisebox.repository.PhysicalBoxRepository,
    private val releaser: com.foodrescue.backend.order.service.OrderReleaser,
    private val cancellation: com.foodrescue.backend.order.service.PaidOrderCancellationExecutor,
    private val refunds: com.foodrescue.backend.payment.repository.RefundRequestRepository,
    private val jdbc: org.springframework.jdbc.core.JdbcTemplate,
    private val em: jakarta.persistence.EntityManager
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
        val activeIds = if (to == StoreStatus.SUSPENDED) jdbc.queryForList("""select id from customer_order where store_id=?
            and status in ('PENDING_PAYMENT','CONFIRMED','HANDOVER_IN_PROGRESS','PENDING_NO_SHOW') order by id""", UUID::class.java, storeId).filterNotNull() else emptyList()
        if (activeIds.isNotEmpty() && request.existingOrderHandling == null)
            throw ApiException(409, "EXISTING_ORDERS_DECISION_REQUIRED", "Store has open orders; specify existingOrderHandling")
        store.status = to
        store.statusReason = if (to == StoreStatus.SUSPENDED) reason else null
        store.statusChangedAt = clock.instant()
        stores.saveAndFlush(store)
        var summary: com.foodrescue.backend.store.dto.StoreSuspensionOrderSummary? = null
        if (to == StoreStatus.SUSPENDED) {
            val lockedBatches = saleBatches.lockAllByStoreId(storeId)
            val lockedOrders = activeIds.map { orders.findForUpdate(it)!! }
            val owner = store.owner
            val hasActor = (owner.status == com.foodrescue.backend.account.AccountStatus.ACTIVE && owner.emailVerifiedAt != null) ||
                (jdbc.queryForObject("""select count(*) from staff_assignment s join account a on a.id=s.account_id
                    join staff_permission sp on sp.assignment_id=s.id join permission p on p.id=sp.permission_id
                    where s.store_id=? and s.status='ACTIVE' and s.revoked_at is null and a.status='ACTIVE'
                    and a.email_verified_at is not null and p.code='PICKUP_HANDLE'""", Long::class.java, storeId)!! > 0)
            val retained = mutableListOf<UUID>(); val unpaid = mutableListOf<UUID>(); val cancelled = mutableListOf<UUID>()
            val retainedBatches = mutableSetOf<UUID>()
            lockedOrders.forEach { order ->
                if (order.status !in com.foodrescue.backend.surprisebox.service.BoxUnfitProcessor.ACTIVE_ORDERS) return@forEach
                val active = allocations.findAllByOrderIdOrderByReservedAt(order.id!!)
                    .filter { it.status in com.foodrescue.backend.surprisebox.service.BoxUnfitProcessor.ACTIVE_ALLOCATIONS }
                val lockedBoxes = active.sortedBy { it.box.id }.map { boxes.findForUpdate(it.box.id!!)!! }
                if (order.status == com.foodrescue.backend.order.OrderStatus.PENDING_PAYMENT) {
                    releaser.release(order, com.foodrescue.backend.order.OrderStatus.CANCELLED, "STORE_SUSPENDED: $reason", admin, "ORDER_CANCELLED_STORE_SUSPENDED")
                    unpaid.add(order.id!!)
                } else {
                    val unsafe = lockedBoxes.filter { it.status == com.foodrescue.backend.surprisebox.BoxStatus.BLOCKED ||
                        !it.safeUntil.isAfter(clock.instant()) || it.safeUntil.isBefore(order.pickupEndsAt) }.map { it.id!! }
                    val keep = request.existingOrderHandling == com.foodrescue.backend.store.StoreSuspendOrderHandling.CONTINUE_IF_SAFE &&
                        hasActor && order.statusReason?.startsWith("HANDOVER_INCIDENT:") != true && unsafe.isEmpty()
                    if (keep) { retained.add(order.id!!); retainedBatches.add(order.saleBatch.id!!); retainedBatches.addAll(lockedBoxes.map { it.currentBatch.id!! }) }
                    else {
                        cancellation.forceCancelStoreFault(order, admin, "STORE_SUSPENDED: $reason", unsafeIds = unsafe)
                        cancelled.add(order.id!!)
                    }
                }
            }
            saleBatches.flush()
            batches.blockBatchesWithoutOrders(storeId)
            val blocked = mutableListOf<UUID>(); val paused = mutableListOf<UUID>()
            lockedBatches.forEach { batch ->
                em.refresh(batch)
                if (batch.status in listOf(com.foodrescue.backend.surprisebox.SaleBatchStatus.DRAFT,
                    com.foodrescue.backend.surprisebox.SaleBatchStatus.OPEN, com.foodrescue.backend.surprisebox.SaleBatchStatus.PAUSED)) {
                    batch.status = if (batch.id in retainedBatches) com.foodrescue.backend.surprisebox.SaleBatchStatus.PAUSED else com.foodrescue.backend.surprisebox.SaleBatchStatus.BLOCKED
                    batch.statusReason = "Store suspended"
                }
                if (batch.status == com.foodrescue.backend.surprisebox.SaleBatchStatus.PAUSED) paused.add(batch.id!!)
                if (batch.status == com.foodrescue.backend.surprisebox.SaleBatchStatus.BLOCKED) blocked.add(batch.id!!)
            }
            summary = com.foodrescue.backend.store.dto.StoreSuspensionOrderSummary(retained, unpaid, cancelled, blocked, paused,
                cancelled.flatMap { refunds.findAllByOrderIdOrderByCreatedAt(it).map { r -> r.id!! } }, hasActor)
            saleBatches.flush()
        }
        history.record(admin, store, StoreHistoryRecorder.STORE, store.id!!,
            if (to == StoreStatus.SUSPENDED) "STORE_SUSPENDED" else "STORE_REACTIVATED", before,
            mapOf("status" to to.name, "reason" to reason, "blockedSaleBatches" to (summary?.blockedSaleBatches ?: 0),
                "pausedSaleBatches" to (summary?.pausedSaleBatches ?: 0), "cancelledUnpaidOrders" to summary?.cancelledUnpaidOrders,
                "cancelledConfirmedOrders" to summary?.cancelledConfirmedOrders, "retainedConfirmedOrders" to summary?.retainedConfirmedOrders,
                "hasAuthorizedHandoverActor" to summary?.hasAuthorizedHandoverActor))
        stores.flush()
        return queries.detail(store).copy(suspensionOrderSummary = summary)
    }
}
