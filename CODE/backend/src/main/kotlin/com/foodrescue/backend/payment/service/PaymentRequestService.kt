// AC-01..06 (T6.2): PaymentRequestService responsibility.
package com.foodrescue.backend.payment.service

import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.order.repository.CustomerOrderRepository
import com.foodrescue.backend.payment.PaymentRequestStatus
import com.foodrescue.backend.payment.dto.PaymentRequestView
import com.foodrescue.backend.payment.gateway.*
import com.foodrescue.backend.payment.gateway.PaymentGatewayException.Kind
import com.foodrescue.backend.payment.repository.PaymentRequestRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class PaymentRequestResult(val view: PaymentRequestView, val created: Boolean)

/**
 * UC-14 steps 1-3 / UC-15. Not transactional on purpose: DB steps run in [PaymentRequestStore], the provider is called between them.
 * After an unclear failure the existing code is looked up at the provider before anything is created again (UC-15 E1).
 */
@Service
class PaymentRequestService(
    private val store: PaymentRequestStore,
    private val gateway: PaymentGateway,
    private val payos: PayOsProperties,
    private val requests: PaymentRequestRepository,
    private val orders: CustomerOrderRepository
) {
    private val log = LoggerFactory.getLogger(PaymentRequestService::class.java)

    fun getOrCreate(customerId: UUID, orderId: UUID): PaymentRequestResult {
        val r = store.reserve(customerId, orderId, gateway.provider)
        if (r.status == PaymentRequestStatus.READY) return PaymentRequestResult(view(r.id!!), false)
        val code = r.providerOrderCode!!
        val link = try {
            gateway.createLink(PaymentLinkRequest(code, r.expectedAmount.toLong(), description(code), payos.returnUrl, payos.cancelUrl, r.expiresAt))
        } catch (e: PaymentGatewayException) {
            if (e.kind == Kind.REJECTED && !e.message.orEmpty().contains("tồn tại") && !e.message.orEmpty().contains("exist")) {
                lookup(code)?.let { return PaymentRequestResult(view(store.complete(r.id!!, it).id!!), true) }
                store.fail(r.id!!)
                log.warn("Payment link rejected for request {}: {}", r.id, e.message)
                throw ApiException(502, "PAYMENT_PROVIDER_REJECTED", "The payment provider refused to create the payment link; try again")
            }
            lookup(code) ?: run {
                log.warn("Payment link outcome unknown for request {}: {}", r.id, e.kind)
                throw ApiException(503, "PAYMENT_PROVIDER_UNAVAILABLE", "The payment provider is not reachable; try again shortly")
            }
        }
        return PaymentRequestResult(view(store.complete(r.id!!, link).id!!), true)
    }

    @Transactional(readOnly = true)
    fun current(customerId: UUID, orderId: UUID): PaymentRequestView {
        val order = orders.findById(orderId).orElse(null)?.takeIf { it.customer.id == customerId } ?: notFound()
        val r = requests.findFirstByOrderIdOrderByCreatedAtDesc(order.id!!) ?: notFound()
        return PaymentRequestStore.toView(r)
    }

    /** UC-15 E1: an existing PENDING link for the code is reused (payOS get-info has no QR; the checkout page shows it). */
    private fun lookup(code: Long): PaymentLink? = try {
        gateway.getLink(code).takeIf { it.status == PaymentLinkStatus.PENDING }
            ?.let { PaymentLink(it.orderCode, it.paymentLinkId, it.amount, CHECKOUT + it.paymentLinkId, "", it.status) }
    } catch (_: PaymentGatewayException) { null }

    /** Built by the store inside its own transaction (self-invocation would bypass @Transactional here). */
    private fun view(id: UUID): PaymentRequestView = store.view(id)

    private fun notFound(): Nothing = throw ApiException(404, "NOT_FOUND", "Resource not found")

    companion object {
        const val CHECKOUT = "https://pay.payos.vn/web/"
        /** payOS: at most 9 characters for bank accounts not linked through payOS. */
        fun description(code: Long) = "FR$code"
    }
}
