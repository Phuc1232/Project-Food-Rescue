// AC-01..14 (T7.2): PickupSessionService responsibility.
package com.foodrescue.backend.pickup.service

import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.security.StoreAuthorizationService
import com.foodrescue.backend.finance.FinancialEventType
import com.foodrescue.backend.finance.entity.FinancialEvent
import com.foodrescue.backend.finance.repository.FinancialEventRepository
import com.foodrescue.backend.order.AllocationStatus
import com.foodrescue.backend.order.OrderStatus
import com.foodrescue.backend.order.entity.CustomerOrder
import com.foodrescue.backend.order.entity.BoxAllocation
import com.foodrescue.backend.order.repository.BoxAllocationRepository
import com.foodrescue.backend.order.repository.CustomerOrderRepository
import com.foodrescue.backend.order.service.OrderMoneyReader
import com.foodrescue.backend.pickup.PickupStatus
import com.foodrescue.backend.pickup.dto.PickupSessionView
import com.foodrescue.backend.pickup.entity.PickupSession
import com.foodrescue.backend.pickup.repository.PickupSessionRepository
import com.foodrescue.backend.store.service.StoreHistoryRecorder
import com.foodrescue.backend.surprisebox.BoxStatus
import com.foodrescue.backend.surprisebox.repository.PhysicalBoxRepository
import com.foodrescue.backend.surprisebox.entity.PhysicalBox
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

@Service
class PickupSessionService(
    private val orders: CustomerOrderRepository, private val sessions: PickupSessionRepository,
    private val allocations: BoxAllocationRepository, private val boxes: PhysicalBoxRepository,
    private val authorization: StoreAuthorizationService, private val accounts: AccountRepository,
    private val money: OrderMoneyReader, private val events: FinancialEventRepository,
    private val history: StoreHistoryRecorder, private val clock: Clock, private val em: EntityManager
) {
    @Transactional
    fun startSession(actorId: UUID, storeId: UUID, orderId: UUID, idempotencyKey: String): PickupSessionView {
        authorization.requirePermission(actorId, storeId, "PICKUP_HANDLE")
        key(idempotencyKey)
        val order = orders.findForUpdate(orderId)?.takeIf { it.store.id == storeId } ?: missing()
        sessions.findByOrderIdAndStatusIn(orderId, listOf(PickupStatus.OPEN, PickupStatus.RECONCILING))?.let {
            if (it.idempotencyKey == idempotencyKey) return view(it)
            conflict("SESSION_ALREADY_OPEN")
        }
        if (order.status != OrderStatus.CONFIRMED) conflict("INVALID_ORDER_STATUS")
        if (order.statusReason?.startsWith("HANDOVER_INCIDENT:") == true) conflict("PICKUP_INCIDENT_PENDING")
        if (sessions.findByIdempotencyKey(idempotencyKey) != null) conflict("IDEMPOTENCY_KEY_CONFLICT")
        if (money.money(order).remainingToPay.signum() != 0) conflict("PAYMENT_INCOMPLETE")
        val now = clock.instant()
        if (now.isBefore(order.pickupStartsAt) || !now.isBefore(order.pickupEndsAt)) conflict("OUTSIDE_PICKUP_WINDOW")
        readyBoxes(order)
        val actor = accounts.getReferenceById(actorId)
        val session = sessions.save(PickupSession(order, actor, PickupStatus.OPEN, now, idempotencyKey = idempotencyKey))
        order.status = OrderStatus.HANDOVER_IN_PROGRESS
        order.statusReason = "HANDOVER_STARTED"
        order.statusChangedAt = now
        history.record(actor, order.store, "CUSTOMER_ORDER", orderId, "HANDOVER_STARTED", null,
            mapOf("sessionId" to session.id, "status" to order.status.name))
        return view(session)
    }

    @Transactional
    fun completeSession(actorId: UUID, storeId: UUID, sessionId: UUID, idempotencyKey: String): PickupSessionView {
        authorization.requirePermission(actorId, storeId, "PICKUP_HANDLE")
        key(idempotencyKey)
        val session = sessions.findById(sessionId).orElse(null) ?: missing()
        val order = orders.findForUpdate(session.order.id!!)?.takeIf { it.store.id == storeId } ?: missing()
        // The session may have been loaded before waiting for the order lock. Re-read its committed state.
        em.refresh(session)
        if (session.status == PickupStatus.COMPLETED) {
            if (session.idempotencyKey == idempotencyKey) return view(session)
            conflict("SESSION_ALREADY_COMPLETED")
        }
        if (session.idempotencyKey != idempotencyKey) conflict("IDEMPOTENCY_KEY_CONFLICT")
        if (session.status != PickupStatus.OPEN || order.status != OrderStatus.HANDOVER_IN_PROGRESS) conflict("INVALID_ORDER_STATUS")
        val ready = readyBoxes(order)
        val now = clock.instant()
        session.status = PickupStatus.COMPLETED
        session.completedAt = now
        order.status = OrderStatus.COMPLETED
        order.completedAt = now
        order.statusReason = "HANDOVER_COMPLETED"
        order.statusChangedAt = now
        ready.forEach { (allocation, box) -> allocation.status = AllocationStatus.DELIVERED; box.status = BoxStatus.DELIVERED }
        events.save(FinancialEvent(order = order, type = FinancialEventType.ORDER_COMPLETED,
            occurredAt = now, businessKey = "ORDER_COMPLETED:${order.id}"))
        history.record(accounts.getReferenceById(actorId), order.store, "CUSTOMER_ORDER", order.id!!,
            "HANDOVER_COMPLETED", null, mapOf("sessionId" to session.id, "status" to order.status.name))
        return view(session)
    }

    private fun readyBoxes(order: CustomerOrder): List<Pair<BoxAllocation, PhysicalBox>> {
        val allocated = allocations.findAllByOrderIdAndStatus(order.id!!, AllocationStatus.CONFIRMED)
        if (allocated.size != order.quantity) conflict("BOXES_NOT_READY")
        return allocated.sortedBy { it.box.id }.map { allocation ->
            val box = boxes.findForUpdate(allocation.box.id!!) ?: conflict("BOXES_NOT_READY")
            if (box.status != BoxStatus.ALLOCATED) conflict("BOXES_NOT_READY")
            allocation to box
        }
    }
    private fun key(key: String) { if (key.isBlank() || key.length > 255) throw ApiException(400, "VALIDATION_ERROR", "Idempotency-Key must contain 1 to 255 characters") }
    private fun view(s: PickupSession) = PickupSessionView(s.id!!, s.order.id!!, s.order.orderCode, s.actor.id!!,
        s.status.name, s.startedAt, s.completedAt, s.idempotencyKey)
    private fun conflict(code: String): Nothing = throw ApiException(409, code, code)
    private fun missing(): Nothing = throw ApiException(404, "NOT_FOUND", "Pickup session or order not found")
}
