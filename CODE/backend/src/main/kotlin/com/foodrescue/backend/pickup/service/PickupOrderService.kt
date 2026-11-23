// AC-01..14 (T7.2): PickupOrderService responsibility.
package com.foodrescue.backend.pickup.service

import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.security.StoreAuthorizationService
import com.foodrescue.backend.order.OrderStatus
import com.foodrescue.backend.order.dto.AllocatedBoxView
import com.foodrescue.backend.order.repository.BoxAllocationRepository
import com.foodrescue.backend.order.repository.CustomerOrderRepository
import com.foodrescue.backend.order.service.OrderMoneyReader
import com.foodrescue.backend.pickup.PickupStatus
import com.foodrescue.backend.pickup.dto.PickupOrderSummaryView
import com.foodrescue.backend.pickup.repository.PickupSessionRepository
import com.foodrescue.backend.store.repository.StoreRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@Service
class PickupOrderService(private val orders: CustomerOrderRepository, private val stores: StoreRepository,
    private val authorization: StoreAuthorizationService, private val allocations: BoxAllocationRepository,
    private val sessions: PickupSessionRepository, private val money: OrderMoneyReader, private val clock: Clock) {
    @Transactional(readOnly = true)
    fun list(actorId: UUID, storeId: UUID, date: LocalDate?, status: OrderStatus?): List<PickupOrderSummaryView> {
        authorization.requirePermission(actorId, storeId, "PICKUP_HANDLE")
        val statuses = listOf(OrderStatus.CONFIRMED, OrderStatus.HANDOVER_IN_PROGRESS)
        if (status != null && status !in statuses) throw ApiException(400, "VALIDATION_ERROR", "Invalid pickup status")
        val zone = ZoneId.of(stores.findById(storeId).orElseThrow().timezone)
        val day = date ?: LocalDate.ofInstant(clock.instant(), zone)
        return orders.findPickupOrders(storeId, status?.let { listOf(it) } ?: statuses,
            day.atStartOfDay(zone).toInstant(), day.plusDays(1).atStartOfDay(zone).toInstant()).map { o ->
            val active = sessions.findByOrderIdAndStatusIn(o.id!!, listOf(PickupStatus.OPEN, PickupStatus.RECONCILING))
            val boxes = allocations.findAllByOrderIdOrderByReservedAt(o.id!!).map {
                AllocatedBoxView(it.box.id!!, it.box.boxCode, it.box.totalListedValue.setScale(0), it.box.status.name,
                    it.status.name, it.reservedAt, it.releasedAt, it.releaseReason)
            }
            PickupOrderSummaryView(o.id!!, o.orderCode, o.status.name, o.customer.displayName, o.quantity,
                o.pickupStartsAt, o.pickupEndsAt, o.pickupAddressSnapshot, boxes, money.money(o), active?.id, active?.status?.name)
        }
    }
}
