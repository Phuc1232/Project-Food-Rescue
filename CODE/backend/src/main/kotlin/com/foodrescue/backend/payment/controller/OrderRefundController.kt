// AC-06 (T6.5): OrderRefundController responsibility.
package com.foodrescue.backend.payment.controller

import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.security.CurrentAccount
import com.foodrescue.backend.order.repository.CustomerOrderRepository
import com.foodrescue.backend.payment.dto.RefundRequestView
import com.foodrescue.backend.payment.repository.RefundRequestRepository
import com.foodrescue.backend.payment.service.RefundViews
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.util.UUID

/** UC-74 for the customer: refunds of my order; "refunded" only after an administrator recorded the transfer. */
@RestController
class OrderRefundController(private val orders: CustomerOrderRepository, private val obligations: RefundRequestRepository, private val views: RefundViews,
                            private val current: CurrentAccount) {
    @GetMapping("/api/v1/orders/{orderId}/refunds")
    @Transactional(readOnly = true)
    fun refunds(@PathVariable orderId: UUID): List<RefundRequestView> {
        val order = orders.findById(orderId).orElse(null)?.takeIf { it.customer.id == current.get().accountId } ?: throw ApiException(404, "NOT_FOUND", "Resource not found")
        return obligations.findAllByOrderIdOrderByCreatedAt(order.id!!).map { views.view(it, false) }
    }
}
