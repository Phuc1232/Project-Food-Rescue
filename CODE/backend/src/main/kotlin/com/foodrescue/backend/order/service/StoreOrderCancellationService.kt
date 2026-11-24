// AC-01..15 (T7.3): StoreOrderCancellationService responsibility.
package com.foodrescue.backend.order.service

import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.order.CancellationInitiator
import com.foodrescue.backend.order.CancellationRequestStatus
import com.foodrescue.backend.order.dto.StoreOrderCancelRequest
import com.foodrescue.backend.order.dto.OrderCancellationView
import com.foodrescue.backend.order.entity.OrderCancellationRequest
import com.foodrescue.backend.order.repository.CustomerOrderRepository
import com.foodrescue.backend.order.repository.OrderCancellationRequestRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

@Service
class StoreOrderCancellationService(private val orders: CustomerOrderRepository, private val requests: OrderCancellationRequestRepository,
    private val accounts: AccountRepository, private val executor: PaidOrderCancellationExecutor,
    private val money: OrderMoneyReader, private val views: OrderCancellationViews, private val clock: Clock) {
    @Transactional
    fun cancelByStore(ownerId: UUID, storeId: UUID, orderId: UUID, r: StoreOrderCancelRequest, key: String?): OrderCancellationView {
        val order = orders.findForUpdate(orderId)?.takeIf { it.store.id == storeId } ?: throw ApiException(404, "NOT_FOUND", "Order not found")
        if (order.store.owner.id != ownerId) throw ApiException(403, "ACCESS_DENIED", "Only the store owner may cancel store orders")
        executor.validateKey(key)
        val fingerprint = executor.fingerprint("STORE_CANCEL", listOf(r.reason!!.trim(), r.boxesUnsafe, r.unsafeBoxIds.distinct().sorted()))
        key?.let { requests.findByOrderIdAndIdempotencyKey(orderId, it) }?.let {
            if (it.requestFingerprint != fingerprint) executor.conflict("IDEMPOTENCY_KEY_REUSED", "Key was used with a different cancellation payload")
            return views.view(it)
        }
        executor.requireCancellable(order)
        val actor = accounts.getReferenceById(ownerId)
        val amount = money.money(order).verifiedPaid
        val refund = executor.execute(order, actor, amount, "STORE_FAULT", "STORE_CANCELLED: ${r.reason.trim()}", r.boxesUnsafe, r.unsafeBoxIds)
        requests.findByOrderIdAndStatusIn(orderId, OrderCancellationService.OPEN)?.let {
            it.status = CancellationRequestStatus.APPROVED; it.storeFault = true; it.reviewer = actor
            it.reviewedAt = clock.instant(); it.reviewNote = "STORE_CANCELLED: ${r.reason.trim()}"
            it.approvedRefundAmount = amount; it.refundRequest = refund
            requests.saveAndFlush(it)
        }
        val req = requests.saveAndFlush(OrderCancellationRequest(order, actor, CancellationInitiator.STORE,
            CancellationRequestStatus.APPROVED, r.reason.trim(), true, clock.instant(), key, fingerprint,
            reviewer = actor, reviewedAt = clock.instant(), reviewNote = "STORE_CANCELLED: ${r.reason.trim()}", approvedRefundAmount = amount, refundRequest = refund))
        return views.view(req)
    }
}
