// AC-01..15 (T7.3): AdminCancellationService responsibility.
package com.foodrescue.backend.order.service
import com.foodrescue.backend.notification.service.NotificationOutboxService
import com.foodrescue.backend.notification.NotificationType as NType

import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.response.PageResponse
import com.foodrescue.backend.common.validation.PageQuery
import com.foodrescue.backend.order.CancellationDecision
import com.foodrescue.backend.order.CancellationRequestStatus
import com.foodrescue.backend.order.dto.*
import com.foodrescue.backend.order.repository.CustomerOrderRepository
import com.foodrescue.backend.order.repository.OrderCancellationRequestRepository
import com.foodrescue.backend.store.service.StoreHistoryRecorder
import jakarta.persistence.EntityManager
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

@Service
@PreAuthorize("hasRole('ADMIN')")
class AdminCancellationService(private val outbox: NotificationOutboxService, private val orders: CustomerOrderRepository, private val requests: OrderCancellationRequestRepository,
    private val accounts: AccountRepository, private val executor: PaidOrderCancellationExecutor, private val views: OrderCancellationViews,
    private val money: OrderMoneyReader, private val history: StoreHistoryRecorder, private val em: EntityManager, private val clock: Clock) {
    @Transactional(readOnly = true)
    fun list(status: CancellationRequestStatus?, page: Int, size: Int): PageResponse<AdminCancellationSummary> {
        val pageable = PageQuery().pageable(page, size, "createdAt")
        val result = if (status == null) requests.findAll(pageable) else requests.findAllByStatus(status, pageable)
        return PageResponse(result.content.map(views::summary), page, size, result.totalElements, result.totalPages)
    }
    @Transactional(readOnly = true)
    fun detail(id: UUID): AdminCancellationDetail = views.detail(requests.findById(id).orElse(null) ?: notFound())

    @Transactional
    fun decide(adminId: UUID, id: UUID, r: AdminCancellationDecisionRequest, key: String?): AdminCancellationDetail {
        // Resolve only the identifier before the order lock, avoiding a stale managed request version.
        val orderId = requests.findOrderId(id) ?: notFound()
        val order = orders.findForUpdate(orderId) ?: notFound()
        val req = requests.findForUpdate(id) ?: notFound()
        em.refresh(req)
        executor.validateKey(key)
        val fingerprint = executor.fingerprint("ADMIN_DECISION", listOf(r.decision, r.reason!!.trim(),
            r.refundAmount?.stripTrailingZeros()?.toPlainString(), r.storeFault, r.releaseBoxesToAvailable))
        if (key != null && req.decisionKey == key) {
            if (req.decisionFingerprint != fingerprint) executor.conflict("IDEMPOTENCY_KEY_REUSED", "Key was used with a different decision payload")
            return views.detail(req)
        }
        if (req.status !in OrderCancellationService.OPEN) executor.conflict("CONFLICT", "Cancellation request has already been processed")
        executor.requireCancellable(order)
        val actor = accounts.getReferenceById(adminId)
        when (r.decision!!) {
            CancellationDecision.REJECT -> req.status = CancellationRequestStatus.REJECTED
            CancellationDecision.REQUEST_INFO -> req.status = CancellationRequestStatus.NEEDS_INFO
            CancellationDecision.APPROVE -> {
                val amount = r.refundAmount ?: executor.invalid("refundAmount is required for approval")
                val verified = money.money(order).verifiedPaid
                if (amount.signum() < 0 || amount.stripTrailingZeros().scale() > 0 || amount > verified)
                    executor.invalid("refundAmount must be whole VND between zero and the verified paid amount")
                if (r.storeFault && amount.compareTo(verified) != 0) executor.invalid("Store fault requires a full refund of verified payments")
                req.refundRequest = executor.execute(order, actor, amount.setScale(0),
                    if (r.storeFault) "STORE_FAULT" else "CUSTOMER_CANCELLED_APPROVED",
                    (if (r.storeFault) "STORE_FAULT_CANCELLED: " else "ADMIN_APPROVED_CANCEL: ") + r.reason.trim(),
                    r.storeFault || !r.releaseBoxesToAvailable)
                req.status = CancellationRequestStatus.APPROVED; req.storeFault = r.storeFault; req.approvedRefundAmount = amount.setScale(0)
            }
        }
        req.reviewer = actor; req.reviewedAt = clock.instant(); req.reviewNote = r.reason.trim()
        req.decisionKey = key; req.decisionFingerprint = fingerprint
        requests.saveAndFlush(req)
        history.record(actor, order.store, "CUSTOMER_ORDER", orderId, "CANCELLATION_${r.decision.name}", null,
            mapOf("requestId" to id, "status" to req.status.name, "refundAmount" to req.approvedRefundAmount))
        if(r.decision!=CancellationDecision.APPROVE) {
            val code=if(r.decision==CancellationDecision.REJECT) "CANCELLATION_REJECTED" else "CANCELLATION_INFO_REQUESTED"
            outbox.orderEvent(order,NType.ORDER_STATUS,code,key="$code:${req.id}:${req.version}",payload=mapOf("requestId" to req.id))
        }
        return views.detail(req)
    }
    private fun notFound(): Nothing = throw ApiException(404, "NOT_FOUND", "Cancellation request not found")
}
