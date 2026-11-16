// AC-03 (T6.4): ReconciliationStore responsibility.
package com.foodrescue.backend.payment.service

import com.foodrescue.backend.order.OrderStatus
import com.foodrescue.backend.order.repository.CustomerOrderRepository
import com.foodrescue.backend.order.service.OrderReleaser
import com.foodrescue.backend.payment.PaymentRequestStatus
import com.foodrescue.backend.payment.gateway.PaymentLinkStatus
import com.foodrescue.backend.payment.repository.PaymentRepository
import com.foodrescue.backend.payment.repository.PaymentRequestRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

enum class ReconcileAction { NONE, EXPIRED_REQUEST, RELEASED_ORDER, MISSING_WEBHOOK }

/** Database side of reconciliation: re-reads under row locks and applies the provider answer (or the order state) at most once. */
@Component
class ReconciliationStore(
    private val requests: PaymentRequestRepository,
    private val orders: CustomerOrderRepository,
    private val payments: PaymentRepository,
    private val releaser: OrderReleaser,
    private val clock: Clock
) {
    /** Requests whose order no longer waits for payment are closed; returns true when the provider link should be cancelled. */
    @Transactional
    fun closeIfOrderEnded(requestId: UUID): Boolean {
        val r = requests.findForUpdate(requestId) ?: return false
        if (r.status !in PaymentRequestStore.LIVE) return false
        val order = orders.findForUpdate(r.order.id!!)!!
        if (order.status == OrderStatus.PENDING_PAYMENT || order.status == OrderStatus.CONFIRMED && r.status == PaymentRequestStatus.READY) return false
        r.status = PaymentRequestStatus.EXPIRED; r.reconciledAt = clock.instant(); r.reconcileNote = "ORDER_${order.status}"
        requests.saveAndFlush(r)
        return order.status != OrderStatus.CONFIRMED
    }

    @Transactional
    fun applyProviderStatus(requestId: UUID, status: PaymentLinkStatus, amountPaid: Long): ReconcileAction {
        val r = requests.findForUpdate(requestId) ?: return ReconcileAction.NONE
        val order = orders.findForUpdate(r.order.id!!)!!
        r.reconciledAt = clock.instant()
        val action = when {
            r.status !in PaymentRequestStore.LIVE || order.status != OrderStatus.PENDING_PAYMENT -> ReconcileAction.NONE
            status == PaymentLinkStatus.CANCELLED || status == PaymentLinkStatus.EXPIRED -> {
                // UC-14 E4: the transfer definitively failed -> release the boxes now.
                r.status = PaymentRequestStatus.EXPIRED; r.reconcileNote = "PROVIDER_$status"
                releaser.release(order, OrderStatus.EXPIRED, "PAYMENT_LINK_$status", null, "ORDER_EXPIRED")
                ReconcileAction.RELEASED_ORDER
            }
            (status == PaymentLinkStatus.PAID || status == PaymentLinkStatus.UNDERPAID || amountPaid > 0) && payments.countByRequestId(r.id!!) == 0L -> {
                // Never invent a payment here (the webhook may still arrive): flag it for an administrator.
                r.reconcileNote = "MISSING_WEBHOOK: provider reports $status, amountPaid=$amountPaid"; ReconcileAction.MISSING_WEBHOOK
            }
            else -> ReconcileAction.NONE
        }
        requests.saveAndFlush(r)
        return action
    }

    @Transactional(readOnly = true)
    fun candidates(): List<Pair<UUID, Long>> = requests.findTop50ByStatusInOrderByCreatedAt(PaymentRequestStore.LIVE)
        .filter { it.order.status != OrderStatus.CONFIRMED && (it.reconcileNote == null || !it.reconcileNote!!.startsWith("MISSING_WEBHOOK")) }.mapNotNull { r -> r.providerOrderCode?.let { r.id!! to it } }
}
