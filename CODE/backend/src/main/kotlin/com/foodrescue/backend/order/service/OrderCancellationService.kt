// AC-04 (T5.3): OrderCancellationService responsibility.
package com.foodrescue.backend.order.service

import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.exception.authenticationFailed
import com.foodrescue.backend.order.OrderStatus
import com.foodrescue.backend.order.dto.OrderView
import com.foodrescue.backend.order.repository.CustomerOrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** UC-17 A2: a customer cancels an unpaid order; holds are released and no zero refund is created. Paid orders: T7.3. */
@Service
class OrderCancellationService(
    private val orders: CustomerOrderRepository,
    private val accounts: AccountRepository,
    private val releaser: OrderReleaser,
    private val views: OrderViews
) {
    @Transactional
    fun cancelUnpaid(customerId: UUID, orderId: UUID, reason: String): OrderView {
        val order = orders.findForUpdate(orderId)?.takeIf { it.customer.id == customerId } ?: throw ApiException(404, "NOT_FOUND", "Resource not found")
        if (order.status != OrderStatus.PENDING_PAYMENT)
            throw ApiException(409, "CONFLICT", "Only unpaid orders can be cancelled here (current status ${order.status})")
        val customer = accounts.findById(customerId).orElse(null) ?: authenticationFailed()
        releaser.release(order, OrderStatus.CANCELLED, "CUSTOMER_CANCELLED: " + reason.trim(), customer, "ORDER_CANCELLED_BY_CUSTOMER")
        return views.view(order)
    }
}
