// AC-01,03,04,05,06 (T6.3): PaymentConfirmationService responsibility.
package com.foodrescue.backend.payment.service
import com.foodrescue.backend.notification.service.NotificationOutboxService
import com.foodrescue.backend.notification.NotificationType as NType

import com.foodrescue.backend.finance.FinancialEventType
import com.foodrescue.backend.finance.LedgerEntryType
import com.foodrescue.backend.finance.entity.FinancialEvent
import com.foodrescue.backend.finance.entity.LedgerEntry
import com.foodrescue.backend.finance.repository.FinancialEventRepository
import com.foodrescue.backend.finance.repository.LedgerEntryRepository
import com.foodrescue.backend.order.AllocationStatus
import com.foodrescue.backend.order.OrderStatus
import com.foodrescue.backend.order.entity.CustomerOrder
import com.foodrescue.backend.order.repository.BoxAllocationRepository
import com.foodrescue.backend.order.repository.CustomerOrderRepository
import com.foodrescue.backend.order.service.OrderExpiryService
import com.foodrescue.backend.order.service.OrderPlacementService
import com.foodrescue.backend.order.service.OrderReleaser
import com.foodrescue.backend.payment.PaymentStatus
import com.foodrescue.backend.payment.entity.Payment
import com.foodrescue.backend.payment.gateway.WebhookPayment
import com.foodrescue.backend.payment.repository.PaymentRepository
import com.foodrescue.backend.payment.repository.PaymentRequestRepository
import com.foodrescue.backend.pickup.service.PickupCodeIssuer
import com.foodrescue.backend.common.exception.ApiException
import org.springframework.dao.DataIntegrityViolationException
import com.foodrescue.backend.store.service.StoreHistoryRecorder
import com.foodrescue.backend.surprisebox.BoxStatus
import com.foodrescue.backend.surprisebox.repository.PhysicalBoxRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock

enum class WebhookOutcome { CONFIRMED, DUPLICATE, REQUIRES_REVIEW, IGNORED }

/**
 * UC-14 steps 5-8. Called only with a signature-verified notification. One transaction under the order row lock: the payment is recorded
 * once (BR-30), and when it pays a still-valid order in full the order, allocations, boxes, financial event and ledger entry change together
 * (BR-25). Anything else is kept as REQUIRES_REVIEW — money is never dropped (BR-26, UC-14 E1/E2).
 */
@Service
class PaymentConfirmationService(private val outbox: NotificationOutboxService,
    private val requests: PaymentRequestRepository,
    private val payments: PaymentRepository,
    private val orders: CustomerOrderRepository,
    private val allocations: BoxAllocationRepository,
    private val boxes: PhysicalBoxRepository,
    private val events: FinancialEventRepository,
    private val ledger: LedgerEntryRepository,
    private val releaser: OrderReleaser,
    private val obligations: RefundObligationService,
    private val history: StoreHistoryRecorder,
    private val pickupCodes: PickupCodeIssuer,
    private val clock: Clock
) {
    private val log = LoggerFactory.getLogger(PaymentConfirmationService::class.java)

    @Transactional
    fun record(provider: String, w: WebhookPayment): WebhookOutcome {
        if (w.transactionCode != "00") return WebhookOutcome.IGNORED
        val now = clock.instant()
        val request = requests.findByProviderAndProviderOrderCode(provider, w.orderCode)
        val order = request?.let { orders.findForUpdate(it.order.id!!) }
        if (payments.existsByProviderAndProviderTransactionId(provider, w.reference)) return WebhookOutcome.DUPLICATE
        val amount = BigDecimal(w.amount)
        val payment = payments.saveAndFlush(Payment(request, order, provider, w.reference, amount, w.currency.ifBlank { "VND" },
            PaymentStatus.RECEIVED, null, now))
        if (order == null) return review(payment, "unknown order code ${w.orderCode}")

        if (order.status == OrderStatus.PENDING_PAYMENT && order.holdExpiresAt.isBefore(now))
            releaser.release(order, OrderStatus.EXPIRED, OrderExpiryService.REASON, null, "ORDER_EXPIRED")   // BR-26: late money never revives the order
        if (order.status != OrderStatus.PENDING_PAYMENT) return review(payment, "order ${order.orderCode} is ${order.status}", order, when (order.status) {
            OrderStatus.EXPIRED -> RefundObligationService.LATE_PAYMENT
            OrderStatus.CANCELLED -> RefundObligationService.ORDER_CANCELLED
            else -> RefundObligationService.ALREADY_PAID
        })
        if (amount.compareTo(order.totalAmount) != 0 || payment.currency != order.currency)
            return review(payment, "amount ${w.amount} ${payment.currency} does not match ${order.totalAmount.toPlainString()} ${order.currency}", order,
                RefundObligationService.AMOUNT_MISMATCH)

        confirm(order, payment, now)
        return WebhookOutcome.CONFIRMED
    }

    private fun confirm(order: CustomerOrder, payment: Payment, now: java.time.Instant) {
        payment.status = PaymentStatus.VERIFIED; payment.verifiedAt = now; payments.save(payment)
        val held = allocations.findAllByOrderIdAndStatus(order.id!!, AllocationStatus.HELD)
        held.forEach { a ->
            a.status = AllocationStatus.CONFIRMED; a.confirmedAt = now
            val box = boxes.findForUpdate(a.box.id!!)!!
            box.status = BoxStatus.ALLOCATED; boxes.save(box)
        }
        allocations.saveAll(held)
        order.status = OrderStatus.CONFIRMED; order.statusReason = "PAYMENT_VERIFIED"; order.statusChangedAt = now
        pickupCodes.issue(order) // T7.1: confirmation and credential issuance succeed or roll back together.
        try { orders.saveAndFlush(order) }
        catch (_: DataIntegrityViolationException) {
            // A credential write failure is retryable, not a duplicate provider transaction.
            throw ApiException(503, "SERVICE_UNAVAILABLE", "Pickup code could not be issued")
        }
        val event = events.saveAndFlush(FinancialEvent(order, FinancialEventType.PAYMENT_RECEIVED, payment = payment, occurredAt = now,
            businessKey = "PAYMENT_RECEIVED:${payment.id}"))
        ledger.save(LedgerEntry(event, order.store, LedgerEntryType.PAYMENT_RECEIPT, payment.receivedAmount, payment.currency, occurredAt = now, businessKey = "PAYMENT_RECEIPT:${payment.id}"))
        history.record(null, order.store, OrderPlacementService.ENTITY, order.id!!, "ORDER_CONFIRMED", mapOf("status" to "PENDING_PAYMENT"),
            mapOf("status" to "CONFIRMED", "paymentId" to payment.id, "boxes" to held.size))
        outbox.orderEvent(order,NType.ORDER_CONFIRMED,"PAYMENT_VERIFIED",key="PAYMENT_VERIFIED:${payment.id}",store=true,payload=mapOf("paymentId" to payment.id,"amount" to payment.receivedAmount))
        log.info("Order {} confirmed by payment {}", order.orderCode, payment.id)
    }

    /** T6.4: with a known order the whole amount becomes a refund obligation in the same transaction (BR-26). */
    private fun review(payment: Payment, why: String, order: CustomerOrder? = null, reasonCode: String? = null): WebhookOutcome {
        payment.status = PaymentStatus.REQUIRES_REVIEW; payments.saveAndFlush(payment)
        if (order != null && reasonCode != null) obligations.open(payment, order, reasonCode, why)
        order?.let { outbox.orderEvent(it,NType.PAYMENT,"PAYMENT_REQUIRES_REVIEW",key="PAYMENT_REVIEW:${payment.id}",payload=mapOf("paymentId" to payment.id,"amount" to payment.receivedAmount)) }
        log.warn("Payment {} needs review: {}", payment.id, why)
        return WebhookOutcome.REQUIRES_REVIEW
    }
}
