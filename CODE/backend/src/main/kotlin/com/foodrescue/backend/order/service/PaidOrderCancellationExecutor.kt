// AC-01..15 (T7.3): PaidOrderCancellationExecutor responsibility.
package com.foodrescue.backend.order.service
import com.foodrescue.backend.notification.service.NotificationOutboxService
import com.foodrescue.backend.notification.NotificationType as NType

import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.order.AllocationStatus
import com.foodrescue.backend.order.OrderStatus
import com.foodrescue.backend.order.entity.CustomerOrder
import com.foodrescue.backend.order.repository.BoxAllocationRepository
import com.foodrescue.backend.payment.PaymentStatus
import com.foodrescue.backend.payment.RefundRequestStatus
import com.foodrescue.backend.payment.entity.RefundRequest
import com.foodrescue.backend.payment.repository.PaymentRepository
import com.foodrescue.backend.payment.repository.RefundRequestRepository
import com.foodrescue.backend.pickup.PickupStatus
import com.foodrescue.backend.pickup.repository.PickupSessionRepository
import com.foodrescue.backend.store.service.StoreHistoryRecorder
import com.foodrescue.backend.surprisebox.BoxStatus
import com.foodrescue.backend.surprisebox.repository.PhysicalBoxRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Clock
import java.util.UUID

/** Caller holds CustomerOrder lock. No bank transfer is made here; only refundable obligations are recorded. */
@Component
class PaidOrderCancellationExecutor(private val outbox: NotificationOutboxService, private val allocations: BoxAllocationRepository, private val boxes: PhysicalBoxRepository,
    private val payments: PaymentRepository, private val refunds: RefundRequestRepository, private val money: OrderMoneyReader,
    private val sessions: PickupSessionRepository, private val history: StoreHistoryRecorder,
    private val cancellations: com.foodrescue.backend.order.repository.OrderCancellationRequestRepository,
    private val em: EntityManager, private val clock: Clock, private val mapper: ObjectMapper) {
    fun validateKey(key: String?) {
        if (key != null && !key.matches(Regex("^[A-Za-z0-9_-]{1,255}$"))) invalid("Invalid Idempotency-Key")
    }
    fun fingerprint(operation: String, payload: Any): String = mapper.writeValueAsString(listOf(operation, payload))
    fun hasSession(order: CustomerOrder) = sessions.findByOrderIdAndStatusIn(order.id!!, listOf(PickupStatus.OPEN, PickupStatus.RECONCILING)) != null
    fun requireCancellable(order: CustomerOrder) {
        if (order.status == OrderStatus.COMPLETED) conflict("ORDER_ALREADY_COMPLETED", "Order is already completed; please use the post-delivery complaint flow")
        if (hasSession(order)) conflict("HANDOVER_IN_PROGRESS", "An active handover session prevents cancellation")
        if (order.status !in listOf(OrderStatus.PENDING_PAYMENT, OrderStatus.CONFIRMED, OrderStatus.HANDOVER_IN_PROGRESS))
            conflict("INVALID_ORDER_STATUS", "Order cannot be cancelled in its current status")
    }
    @Transactional(propagation = Propagation.MANDATORY)
    fun execute(order: CustomerOrder, actor: Account, amount: BigDecimal, reasonCode: String, reason: String,
                blockAll: Boolean, unsafeIds: Collection<UUID> = emptyList()): RefundRequest? {
        val active = allocations.findAllByOrderIdOrderByReservedAt(order.id!!).filter { it.status in listOf(AllocationStatus.HELD, AllocationStatus.CONFIRMED) }.sortedBy { it.box.id }
        if (!active.map { it.box.id }.containsAll(unsafeIds)) conflict("INVALID_UNSAFE_BOX", "Unsafe boxes must belong to this order's active allocations")
        val amounts = money.money(order)
        if (amount > amounts.verifiedPaid - amounts.refunded - amounts.refundInProgress) conflict("REFUND_AMOUNT_CONFLICT", "Refund exceeds the remaining verified funds")
        val verified = payments.findAllByOrderIdOrderByReceivedAt(order.id!!).filter { it.status == PaymentStatus.VERIFIED }.sortedBy { it.id }
        // Existing obligations reserve their entire payment. T6.5 enforces one obligation per payment.
        val available = verified.map { payments.findForUpdate(it.id!!)!! }.filterNot { refunds.existsByPaymentId(it.id!!) }
        if (available.fold(BigDecimal.ZERO) { sum, p -> sum + p.receivedAmount } < amount)
            conflict("REFUND_ALREADY_REQUESTED", "Original payment already has a refund obligation")
        val now = clock.instant()
        active.forEach { a ->
            em.lock(a, LockModeType.PESSIMISTIC_WRITE)
            val box = boxes.findForUpdate(a.box.id!!) ?: conflict("BOXES_NOT_READY", "Allocated box is missing")
            a.status = AllocationStatus.RELEASED; a.releasedAt = now; a.releaseReason = reason
            if (blockAll || box.id in unsafeIds || box.status == BoxStatus.BLOCKED) box.status = BoxStatus.BLOCKED
            else if (box.status in listOf(BoxStatus.RESERVED, BoxStatus.ALLOCATED)) box.status = BoxStatus.AVAILABLE
        }
        order.status = OrderStatus.CANCELLED; order.statusChangedAt = now; order.statusReason = reason
        var remaining = amount
        val obligations = available.mapNotNull { p ->
            val part = remaining.min(p.receivedAmount)
            if (part.signum() <= 0) null else {
                remaining -= part
                refunds.save(RefundRequest(order = order, reason = reason, requestedAmount = part.setScale(0),
                    status = RefundRequestStatus.PENDING, payment = p, reasonCode = reasonCode))
            }
        }
        history.record(actor, order.store, "CUSTOMER_ORDER", order.id!!, "ORDER_CANCELLED", null,
            mapOf("status" to "CANCELLED", "reason" to reason, "refundAmount" to amount, "refundRequestIds" to obligations.map { it.id }))
        outbox.orderEvent(order,NType.ORDER_CANCELLED,"ORDER_CANCELLED",store=true,payload=mapOf("refundAmount" to amount))
        return obligations.firstOrNull()
    }
    /** System/store safety intervention. Caller holds the order lock; normal customer cancellation keeps its existing guards. */
    @Transactional(propagation = Propagation.MANDATORY)
    fun forceCancelStoreFault(order: CustomerOrder, actor: Account, reason: String,
                              blockAll: Boolean = false, unsafeIds: Collection<UUID> = emptyList()): RefundRequest? {
        if (order.status !in listOf(OrderStatus.CONFIRMED, OrderStatus.HANDOVER_IN_PROGRESS, OrderStatus.PENDING_NO_SHOW))
            conflict("INVALID_ORDER_STATUS", "Order is not eligible for forced store-fault cancellation")
        sessions.findByOrderIdAndStatusIn(order.id!!, listOf(PickupStatus.OPEN, PickupStatus.RECONCILING))?.let {
            it.status = PickupStatus.CANCELLED; sessions.saveAndFlush(it)
        }
        order.pickupCodeHash = null; order.pickupCodeEncrypted = null; order.pickupCodeKeyId = null
        val amount = money.money(order).verifiedPaid
        val refund = execute(order, actor, amount, "STORE_FAULT", reason, blockAll, unsafeIds)
        cancellations.findByOrderIdAndStatusIn(order.id!!, OrderCancellationService.OPEN)?.let {
            it.status = com.foodrescue.backend.order.CancellationRequestStatus.APPROVED; it.storeFault = true
            it.approvedRefundAmount = amount; it.refundRequest = refund; it.reviewer = actor
            it.reviewedAt = clock.instant(); it.reviewNote = reason; cancellations.saveAndFlush(it)
        }
        cancellations.save(com.foodrescue.backend.order.entity.OrderCancellationRequest(order, actor,
            com.foodrescue.backend.order.CancellationInitiator.STORE, com.foodrescue.backend.order.CancellationRequestStatus.APPROVED,
            reason, true, clock.instant(), requestFingerprint = fingerprint("FORCED_STORE_FAULT", reason), reviewer = actor,
            reviewedAt = clock.instant(), reviewNote = reason, approvedRefundAmount = amount, refundRequest = refund))
        return refund
    }
    fun invalid(message: String): Nothing = throw ApiException(400, "VALIDATION_FAILED", message)
    fun conflict(code: String, message: String): Nothing = throw ApiException(409, code, message)
}
