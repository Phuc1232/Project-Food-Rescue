// AC-01,03,04 (T5.3): OrderReleaser responsibility.
package com.foodrescue.backend.order.service

import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.order.AllocationStatus
import com.foodrescue.backend.order.OrderStatus
import com.foodrescue.backend.order.entity.CustomerOrder
import com.foodrescue.backend.order.repository.BoxAllocationRepository
import com.foodrescue.backend.order.repository.CustomerOrderRepository
import com.foodrescue.backend.store.service.StoreHistoryRecorder
import com.foodrescue.backend.surprisebox.BoxStatus
import com.foodrescue.backend.surprisebox.repository.PhysicalBoxRepository
import org.springframework.stereotype.Component
import java.time.Clock

/**
 * Ends an unpaid order (EXPIRED or CANCELLED) and releases its HELD boxes. The caller holds the order row lock and has checked the status.
 * A released box goes back to AVAILABLE; whether it is sellable again is decided by the availability rule (BR-24, T4.3).
 */
@Component
class OrderReleaser(
    private val orders: CustomerOrderRepository,
    private val allocations: BoxAllocationRepository,
    private val boxes: PhysicalBoxRepository,
    private val history: StoreHistoryRecorder,
    private val clock: Clock
) {
    fun release(order: CustomerOrder, status: OrderStatus, reason: String, actor: Account?, action: String) {
        val now = clock.instant()
        val held = allocations.findAllByOrderIdAndStatus(order.id!!, AllocationStatus.HELD)
        held.forEach { a ->
            a.status = AllocationStatus.RELEASED; a.releasedAt = now; a.releaseReason = reason
            val box = boxes.findForUpdate(a.box.id!!)!!
            if (box.status == BoxStatus.RESERVED) { box.status = BoxStatus.AVAILABLE; boxes.save(box) }
        }
        allocations.saveAll(held)
        val before = order.status
        order.status = status; order.statusReason = reason; order.statusChangedAt = now
        orders.saveAndFlush(order)
        history.record(actor, order.store, OrderPlacementService.ENTITY, order.id!!, action, mapOf("status" to before.name),
            mapOf("status" to status.name, "reason" to reason, "releasedBoxes" to held.size))
    }
}
