// AC-01..15 (T7.3): OrderCancellationService responsibility.
package com.foodrescue.backend.order.service

import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.file.entity.Attachment
import com.foodrescue.backend.file.entity.StoredFile
import com.foodrescue.backend.file.repository.AttachmentRepository
import com.foodrescue.backend.order.CancellationInitiator
import com.foodrescue.backend.order.CancellationRequestStatus
import com.foodrescue.backend.order.OrderStatus
import com.foodrescue.backend.order.dto.*
import com.foodrescue.backend.order.entity.CustomerOrder
import com.foodrescue.backend.order.entity.OrderCancellationRequest
import com.foodrescue.backend.order.repository.CustomerOrderRepository
import com.foodrescue.backend.order.repository.OrderCancellationRequestRepository
import com.foodrescue.backend.payment.repository.RefundRequestRepository
import com.foodrescue.backend.payment.service.RefundViews
import com.foodrescue.backend.store.service.StoreHistoryRecorder
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.util.UUID

data class CancellationSubmitResult(val request: OrderCancellationView, val created: Boolean)

@Service
class OrderCancellationService(private val orders: CustomerOrderRepository, private val accounts: AccountRepository,
    private val cancellations: OrderCancellationRequestRepository, private val executor: PaidOrderCancellationExecutor,
    private val releaser: OrderReleaser, private val ordersView: OrderViews, private val views: OrderCancellationViews,
    private val money: OrderMoneyReader, private val refunds: RefundRequestRepository, private val refundViews: RefundViews,
    private val attachments: AttachmentRepository, private val em: EntityManager, private val history: StoreHistoryRecorder,
    private val clock: Clock) {
    @Transactional(readOnly = true)
    fun preview(customerId: UUID, orderId: UUID): OrderCancellationPreview {
        val o = owned(customerId, orderId, false)
        val active = executor.hasSession(o)
        val immediate = !active && (o.status == OrderStatus.PENDING_PAYMENT || (o.status == OrderStatus.CONFIRMED && clock.instant().isBefore(o.pickupStartsAt)))
        val review = !active && (o.status == OrderStatus.CONFIRMED && !clock.instant().isBefore(o.pickupStartsAt) || o.status == OrderStatus.HANDOVER_IN_PROGRESS)
        val blocked = when { o.status == OrderStatus.COMPLETED -> "ORDER_ALREADY_COMPLETED"; active -> "HANDOVER_IN_PROGRESS"; !immediate && !review -> "INVALID_ORDER_STATUS"; else -> null }
        return OrderCancellationPreview(orderId, o.status.name, immediate, review,
            if (immediate) (if (o.status == OrderStatus.PENDING_PAYMENT) BigDecimal.ZERO else money.money(o).verifiedPaid) else null,
            blocked, ordersView.view(o).policy)
    }

    @Transactional
    fun cancelUnpaid(customerId: UUID, orderId: UUID, reason: String): OrderView = cancelImmediate(customerId, orderId, reason, null)

    @Transactional
    fun cancelImmediate(customerId: UUID, orderId: UUID, reason: String, key: String?): OrderView {
        val o = owned(customerId, orderId, true)
        executor.validateKey(key)
        val fingerprint = executor.fingerprint("CUSTOMER_IMMEDIATE", reason.trim())
        replay(orderId, key, fingerprint)?.let { return ordersView.view(o) }
        executor.requireCancellable(o)
        if (o.status == OrderStatus.HANDOVER_IN_PROGRESS || o.status == OrderStatus.CONFIRMED && !clock.instant().isBefore(o.pickupStartsAt))
            executor.conflict("CANCELLATION_REQUIRES_REVIEW", "Pickup has started; submit a cancellation request for admin review")
        val actor = accounts.getReferenceById(customerId)
        val amount = if (o.status == OrderStatus.PENDING_PAYMENT) BigDecimal.ZERO else money.money(o).verifiedPaid
        val refund = if (o.status == OrderStatus.PENDING_PAYMENT) {
            releaser.release(o, OrderStatus.CANCELLED, "CUSTOMER_CANCELLED: ${reason.trim()}", actor, "ORDER_CANCELLED_BY_CUSTOMER"); null
        } else executor.execute(o, actor, amount, "CUSTOMER_CANCELLED_BEFORE_PICKUP", "CUSTOMER_CANCELLED: ${reason.trim()}", false)
        cancellations.saveAndFlush(OrderCancellationRequest(o, actor, CancellationInitiator.CUSTOMER, CancellationRequestStatus.APPROVED,
            reason.trim(), false, clock.instant(), key, fingerprint, approvedRefundAmount = amount, refundRequest = refund))
        return ordersView.view(o)
    }

    @Transactional
    fun submitRequest(customerId: UUID, orderId: UUID, r: CustomerCancellationSubmitRequest, key: String?): CancellationSubmitResult {
        val o = owned(customerId, orderId, true)
        executor.validateKey(key)
        val fingerprint = executor.fingerprint("CUSTOMER_REQUEST", listOf(r.reason!!.trim(), r.attachmentIds.distinct().sorted()))
        replay(orderId, key, fingerprint)?.let { return CancellationSubmitResult(views.view(it), false) }
        executor.requireCancellable(o)
        if (o.status !in listOf(OrderStatus.CONFIRMED, OrderStatus.HANDOVER_IN_PROGRESS)) executor.conflict("INVALID_ORDER_STATUS", "Only confirmed orders can request review")
        if (o.status == OrderStatus.CONFIRMED && clock.instant().isBefore(o.pickupStartsAt)) executor.conflict("IMMEDIATE_CANCELLATION_AVAILABLE", "Cancel immediately before pickup starts")
        if (cancellations.findByOrderIdAndStatusIn(orderId, OPEN) != null) executor.conflict("CANCELLATION_ALREADY_REQUESTED", "An open cancellation request already exists")
        val req = cancellations.save(OrderCancellationRequest(o, accounts.getReferenceById(customerId), CancellationInitiator.CUSTOMER,
            CancellationRequestStatus.SUBMITTED, r.reason.trim(), false, clock.instant(), key, fingerprint))
        attach(customerId, req, r.attachmentIds)
        history(req, customerId, "CANCELLATION_REQUEST_SUBMITTED")
        cancellations.flush()
        return CancellationSubmitResult(views.view(req), true)
    }

    @Transactional
    fun supplement(customerId: UUID, orderId: UUID, requestId: UUID, r: CustomerCancellationSupplementRequest): OrderCancellationView {
        owned(customerId, orderId, true)
        val req = request(orderId, requestId)
        if (req.status != CancellationRequestStatus.NEEDS_INFO) executor.conflict("INVALID_REQUEST_STATUS", "Request must need more information")
        attach(customerId, req, r.attachmentIds)
        req.reason += "\n" + r.reason!!.trim()
        req.status = CancellationRequestStatus.SUBMITTED
        cancellations.saveAndFlush(req)
        history(req, customerId, "CANCELLATION_REQUEST_SUPPLEMENTED")
        return views.view(req)
    }

    @Transactional
    fun withdraw(customerId: UUID, orderId: UUID, requestId: UUID): OrderCancellationView {
        owned(customerId, orderId, true)
        val req = request(orderId, requestId)
        if (req.status !in OPEN) executor.conflict("INVALID_REQUEST_STATUS", "Only open requests can be withdrawn")
        req.status = CancellationRequestStatus.WITHDRAWN
        history(req, customerId, "CANCELLATION_REQUEST_WITHDRAWN")
        return views.view(req)
    }

    @Transactional(readOnly = true)
    fun customerResult(customerId: UUID, orderId: UUID): OrderCancellationStatusView {
        val o = owned(customerId, orderId, false)
        return OrderCancellationStatusView(orderId, o.status.name, cancellations.findAllByOrderIdOrderByRequestedAtDesc(orderId).map(views::view),
            refunds.findAllByOrderIdOrderByCreatedAt(orderId).map { refundViews.view(it, false) })
    }

    private fun replay(orderId: UUID, key: String?, fingerprint: String): OrderCancellationRequest? = key?.let {
        cancellations.findByOrderIdAndIdempotencyKey(orderId, it)?.also { req ->
            if (req.requestFingerprint != fingerprint) executor.conflict("IDEMPOTENCY_KEY_REUSED", "Key was used with a different cancellation payload")
        }
    }
    private fun owned(customerId: UUID, orderId: UUID, lock: Boolean): CustomerOrder =
        (if (lock) orders.findForUpdate(orderId) else orders.findById(orderId).orElse(null))?.takeIf { it.customer.id == customerId } ?: notFound()
    private fun request(orderId: UUID, id: UUID) = cancellations.findForUpdate(id)?.takeIf { it.order.id == orderId } ?: notFound()
    private fun attach(customerId: UUID, req: OrderCancellationRequest, ids: List<UUID>) {
        ids.distinct().sorted().forEach { id ->
            val file = em.find(StoredFile::class.java, id, LockModeType.PESSIMISTIC_WRITE)?.takeIf { it.uploadedBy.id == customerId }
                ?: executor.invalid("Evidence file not found or not owned by you")
            if (attachments.findAllByFileId(id).isNotEmpty()) executor.invalid("Evidence file is already attached")
            attachments.save(Attachment(file, orderCancellationRequest = req))
        }
        em.flush()
    }
    private fun history(r: OrderCancellationRequest, actorId: UUID, action: String) = history.record(accounts.getReferenceById(actorId), r.order.store,
        "CUSTOMER_ORDER", r.order.id!!, action, null, mapOf("requestId" to r.id, "status" to r.status.name))
    private fun notFound(): Nothing = throw ApiException(404, "NOT_FOUND", "Order or cancellation request not found")
    companion object { val OPEN = listOf(CancellationRequestStatus.SUBMITTED, CancellationRequestStatus.NEEDS_INFO) }
}
