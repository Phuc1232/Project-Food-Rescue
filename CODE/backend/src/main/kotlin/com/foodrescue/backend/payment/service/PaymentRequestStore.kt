// AC-01..05 (T6.2): PaymentRequestStore responsibility.
package com.foodrescue.backend.payment.service

import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.order.OrderStatus
import com.foodrescue.backend.order.repository.CustomerOrderRepository
import com.foodrescue.backend.payment.PaymentRequestStatus
import com.foodrescue.backend.payment.dto.PaymentRequestView
import com.foodrescue.backend.payment.entity.PaymentRequest
import com.foodrescue.backend.payment.gateway.PaymentLink
import com.foodrescue.backend.payment.repository.PaymentRequestRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.util.UUID

/** The two database steps around the provider call (CLAUDE.md §5): reserve the intent, then record the outcome. */
@Component
class PaymentRequestStore(
    private val orders: CustomerOrderRepository,
    private val requests: PaymentRequestRepository,
    private val jdbc: JdbcTemplate,
    private val clock: Clock
) {
    /** Returns the live request (READY = reuse as is, PENDING = outcome unknown) or a new PENDING one; order row locked. */
    @Transactional
    fun reserve(customerId: UUID, orderId: UUID, provider: String): PaymentRequest {
        val order = orders.findForUpdate(orderId)?.takeIf { it.customer.id == customerId } ?: throw ApiException(404, "NOT_FOUND", "Resource not found")
        val now = clock.instant()
        if (order.status != OrderStatus.PENDING_PAYMENT || order.holdExpiresAt.isBefore(now.plus(MIN_HOLD)))
            throw ApiException(409, "ORDER_NOT_PAYABLE", "Order is not waiting for payment (status ${order.status}) or its hold ends too soon")
        requests.findFirstByOrderIdAndStatusIn(orderId, LIVE)?.let { return it }
        val code = jdbc.queryForObject("SELECT nextval('payment_order_code_seq')", Long::class.java)!!
        return requests.saveAndFlush(PaymentRequest(order, provider, null, "order:$orderId:code:$code", order.totalAmount, order.currency, null,
            order.holdExpiresAt, PaymentRequestStatus.PENDING, providerOrderCode = code))
    }

    @Transactional
    fun complete(requestId: UUID, link: PaymentLink): PaymentRequest {
        val r = requests.findForUpdate(requestId)!!
        if (r.status == PaymentRequestStatus.PENDING) {
            r.providerRequestId = link.paymentLinkId; r.paymentUrl = link.checkoutUrl; r.qrCode = link.qrCode.ifEmpty { null }
            r.status = PaymentRequestStatus.READY
            requests.saveAndFlush(r)
        }
        return r
    }

    @Transactional
    fun fail(requestId: UUID): PaymentRequest {
        val r = requests.findForUpdate(requestId)!!
        if (r.status == PaymentRequestStatus.PENDING) { r.status = PaymentRequestStatus.FAILED; requests.saveAndFlush(r) }
        return r
    }

    @Transactional(readOnly = true)
    fun view(id: UUID): PaymentRequestView = toView(requests.findById(id).orElseThrow())

    companion object {
        /** Call inside a transaction (lazy order). */
        fun toView(r: PaymentRequest) = PaymentRequestView(r.id!!, r.order.id!!, r.order.orderCode, r.providerOrderCode, r.provider, r.status.name,
            r.expectedAmount.setScale(0), r.currency, r.paymentUrl, r.qrCode, r.providerOrderCode?.let(PaymentRequestService::description), r.expiresAt,
            r.order.status.name)

        val LIVE = setOf(PaymentRequestStatus.PENDING, PaymentRequestStatus.READY)
        val MIN_HOLD: Duration = Duration.ofSeconds(60)
    }
}
