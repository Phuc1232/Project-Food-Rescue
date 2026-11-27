// AC-01..14 (T7.2): PickupIncidentService responsibility.
package com.foodrescue.backend.pickup.service
import com.foodrescue.backend.notification.service.NotificationOutboxService
import com.foodrescue.backend.notification.NotificationType as NType

import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.security.StoreAuthorizationService
import com.foodrescue.backend.order.AllocationStatus
import com.foodrescue.backend.order.OrderStatus
import com.foodrescue.backend.order.repository.BoxAllocationRepository
import com.foodrescue.backend.order.repository.CustomerOrderRepository
import com.foodrescue.backend.pickup.PickupStatus
import com.foodrescue.backend.pickup.dto.PickupIncidentCreateRequest
import com.foodrescue.backend.pickup.dto.PickupIncidentView
import com.foodrescue.backend.pickup.entity.PickupIncident
import com.foodrescue.backend.pickup.repository.PickupIncidentRepository
import com.foodrescue.backend.pickup.repository.PickupSessionRepository
import com.foodrescue.backend.store.service.StoreHistoryRecorder
import com.foodrescue.backend.surprisebox.BoxStatus
import com.foodrescue.backend.surprisebox.repository.PhysicalBoxRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

@Service
class PickupIncidentService(private val outbox: NotificationOutboxService, private val orders: CustomerOrderRepository, private val sessions: PickupSessionRepository,
    private val incidents: PickupIncidentRepository, private val allocations: BoxAllocationRepository,
    private val boxes: PhysicalBoxRepository, private val accounts: AccountRepository,
    private val authorization: StoreAuthorizationService, private val history: StoreHistoryRecorder, private val clock: Clock) {
    @Transactional
    fun recordIncident(actorId: UUID, storeId: UUID, orderId: UUID, request: PickupIncidentCreateRequest): PickupIncidentView {
        authorization.requirePermission(actorId, storeId, "PICKUP_HANDLE")
        val order = orders.findForUpdate(orderId)?.takeIf { it.store.id == storeId }
            ?: throw ApiException(404, "NOT_FOUND", "Order not found")
        if (order.status == OrderStatus.COMPLETED) throw ApiException(409, "ORDER_ALREADY_COMPLETED", "Use the post-delivery complaint process")
        if (order.status !in listOf(OrderStatus.CONFIRMED, OrderStatus.HANDOVER_IN_PROGRESS, OrderStatus.PENDING_NO_SHOW))
            throw ApiException(409, "INVALID_ORDER_STATUS", "Order cannot have a pickup incident")
        val affected = request.affectedBoxIds.orEmpty().distinct().sorted()
        val allocated = allocations.findAllByOrderIdAndStatus(orderId, AllocationStatus.CONFIRMED).map { it.box.id }.toSet()
        if (!allocated.containsAll(affected)) throw ApiException(409, "INVALID_AFFECTED_BOX", "Affected boxes must be allocated to this order")
        affected.forEach { id ->
            val box = boxes.findForUpdate(id) ?: throw ApiException(404, "NOT_FOUND", "Box not found")
            box.status = BoxStatus.BLOCKED
        }
        val session = sessions.findByOrderIdAndStatusIn(orderId, listOf(PickupStatus.OPEN, PickupStatus.RECONCILING))
        session?.status = PickupStatus.CANCELLED
        // Keep HANDOVER_IN_PROGRESS while review is pending to prevent another handover or no-show.
        if (order.status == OrderStatus.PENDING_NO_SHOW) order.status = OrderStatus.HANDOVER_IN_PROGRESS
        order.statusReason = "HANDOVER_INCIDENT: ${request.reason}"
        order.statusChangedAt = clock.instant()
        val actor = accounts.getReferenceById(actorId)
        val incident = incidents.save(PickupIncident(order = order, session = session, actor = actor,
            reason = request.reason, customerArrived = request.customerArrived!!, occurredAt = clock.instant(),
            resolution = request.resolution ?: "PENDING_REVIEW"))
        history.record(actor, order.store, "CUSTOMER_ORDER", orderId, "PICKUP_INCIDENT_RECORDED", null,
            mapOf("incidentId" to incident.id, "sessionId" to session?.id, "affectedBoxIds" to affected))
        outbox.orderEvent(order,NType.ORDER_STATUS,"HANDOVER_INCIDENT",key="HANDOVER_INCIDENT:${incident.id}",payload=mapOf("incidentId" to incident.id))
        return PickupIncidentView(incident.id!!, orderId, session?.id, actorId, incident.reason,
            incident.customerArrived, incident.occurredAt, incident.resolution)
    }
}
