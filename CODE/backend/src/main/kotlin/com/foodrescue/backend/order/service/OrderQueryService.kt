// AC-05,06 (T5.3): OrderQueryService responsibility.
package com.foodrescue.backend.order.service

import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.response.FieldViolation
import com.foodrescue.backend.common.response.PageResponse
import com.foodrescue.backend.common.security.StoreAuthorizationService
import com.foodrescue.backend.order.OrderStatus
import com.foodrescue.backend.order.dto.*
import com.foodrescue.backend.order.entity.CustomerOrder
import com.foodrescue.backend.order.repository.BoxAllocationRepository
import com.foodrescue.backend.order.repository.CustomerOrderRepository
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/** UC-19, UC-20, UC-22 (customer: own orders only, others look absent) and UC-54, UC-55 (owner or staff ORDER_VIEW). */
@Service
class OrderQueryService(
    private val orders: CustomerOrderRepository,
    private val allocations: BoxAllocationRepository,
    private val authorization: StoreAuthorizationService,
    private val money: OrderMoneyReader,
    private val views: OrderViews,
    private val entityManager: EntityManager
) {
    @Transactional(readOnly = true)
    fun mine(customerId: UUID, status: OrderStatus?, from: Instant?, to: Instant?, page: Int, size: Int) =
        list("o.customer.id = :owner", customerId, status, from, to, page, size, withCustomer = false)

    @Transactional(readOnly = true)
    fun myMoney(customerId: UUID, orderId: UUID): OrderMoneyView = money.money(own(customerId, orderId))

    @Transactional(readOnly = true)
    fun forStore(accountId: UUID, storeId: UUID, status: OrderStatus?, from: Instant?, to: Instant?, page: Int, size: Int): PageResponse<OrderSummary> {
        authorization.requirePermission(accountId, storeId, "ORDER_VIEW")
        return list("o.store.id = :owner", storeId, status, from, to, page, size, withCustomer = true)
    }

    @Transactional(readOnly = true)
    fun storeDetail(accountId: UUID, storeId: UUID, orderId: UUID): StoreOrderDetail {
        authorization.requirePermission(accountId, storeId, "ORDER_VIEW")
        val o = orders.findById(orderId).orElse(null)?.takeIf { it.store.id == storeId } ?: notFound()
        val boxes = allocations.findAllByOrderIdOrderByReservedAt(o.id!!).map {
            AllocatedBoxView(it.box.id!!, it.box.boxCode, it.box.totalListedValue.setScale(0), it.box.status.name, it.status.name, it.reservedAt,
                it.releasedAt, it.releaseReason)
        }
        return StoreOrderDetail(views.view(o), o.customer.displayName, boxes, money.money(o))
    }

    fun own(customerId: UUID, orderId: UUID): CustomerOrder = orders.findById(orderId).orElse(null)?.takeIf { it.customer.id == customerId } ?: notFound()

    private fun list(scope: String, owner: UUID, status: OrderStatus?, from: Instant?, to: Instant?, page: Int, size: Int, withCustomer: Boolean): PageResponse<OrderSummary> {
        val errors = mutableListOf<FieldViolation>()
        if (page < 0) errors += FieldViolation("page", "Must not be negative")
        if (size !in 1..50) errors += FieldViolation("size", "Must be between 1 and 50")
        if (from != null && to != null && !from.isBefore(to)) errors += FieldViolation("to", "Must be after from")
        if (errors.isNotEmpty()) throw ApiException(400, "VALIDATION_FAILED", "Invalid request", errors)
        val where = mutableListOf(scope); val params = mutableMapOf<String, Any>("owner" to owner)
        status?.let { where += "o.status = :status"; params["status"] = it }
        from?.let { where += "o.acceptedAt >= :from"; params["from"] = it }
        to?.let { where += "o.acceptedAt < :to"; params["to"] = it }
        val clause = where.joinToString(" and ")
        val total = entityManager.createQuery("select count(o) from CustomerOrder o where $clause", java.lang.Long::class.java)
            .also { q -> params.forEach { (k, v) -> q.setParameter(k, v) } }.singleResult.toLong()
        val rows = entityManager.createQuery("select o from CustomerOrder o where $clause order by o.acceptedAt desc, o.id", CustomerOrder::class.java)
            .also { q -> params.forEach { (k, v) -> q.setParameter(k, v) } }.setFirstResult(page * size).setMaxResults(size).resultList
        return PageResponse(rows.map { OrderSummary(it.id!!, it.orderCode, it.status.name, it.store.id!!, it.store.name, it.quantity,
            it.totalAmount.setScale(0), it.pickupStartsAt, it.pickupEndsAt, it.acceptedAt, it.holdExpiresAt, if (withCustomer) it.customer.displayName else null) },
            page, size, total, ((total + size - 1) / size).toInt())
    }

    private fun notFound(): Nothing = throw ApiException(404, "NOT_FOUND", "Resource not found")
}
