// AC-03..10,12 (T7.1): PickupCodeService authorizes and resolves pickup credentials without handing over boxes.
package com.foodrescue.backend.pickup.service

import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.security.StoreAuthorizationService
import com.foodrescue.backend.middleware.ratelimit.RateLimitPolicy
import com.foodrescue.backend.middleware.ratelimit.RateLimitService
import com.foodrescue.backend.order.AllocationStatus
import com.foodrescue.backend.order.OrderStatus
import com.foodrescue.backend.order.dto.AllocatedBoxView
import com.foodrescue.backend.order.entity.CustomerOrder
import com.foodrescue.backend.order.repository.BoxAllocationRepository
import com.foodrescue.backend.order.repository.CustomerOrderRepository
import com.foodrescue.backend.order.service.OrderMoneyReader
import com.foodrescue.backend.pickup.dto.PickupCodeView
import com.foodrescue.backend.pickup.dto.PickupVerificationView
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.util.UUID

@Service
class PickupCodeService(private val orders: CustomerOrderRepository, private val allocations: BoxAllocationRepository,
                        private val authorization: StoreAuthorizationService, private val limits: RateLimitService,
                        private val codec: PickupCodeCodec, private val money: OrderMoneyReader, private val clock: Clock,
                        transactionManager: PlatformTransactionManager) {
    private val reads = TransactionTemplate(transactionManager).apply { isReadOnly = true }
    @Transactional(readOnly = true)
    fun mine(customerId: UUID, orderId: UUID): PickupCodeView {
        val o = orders.findById(orderId).orElse(null)?.takeIf { it.customer.id == customerId } ?: notFound()
        val usable = usable(o)
        val code = if (usable) {
            val encrypted = o.pickupCodeEncrypted ?: unavailable()
            val keyId = o.pickupCodeKeyId ?: unavailable()
            codec.decrypt(o.id!!, keyId, encrypted).also { if (codec.hash(it) != o.pickupCodeHash) unavailable() }
        } else null
        return PickupCodeView(o.id!!, o.orderCode, o.status.name, code, usable, o.pickupAddressSnapshot, o.pickupStartsAt, o.pickupEndsAt, money.money(o))
    }

    fun verify(actorId: UUID, storeId: UUID, input: String?): PickupVerificationView {
        authorization.requirePermission(actorId, storeId, "PICKUP_HANDLE")
        limits.check(RateLimitPolicy.PICKUP_VERIFY_ACCOUNT, actorId.toString())
        limits.check(RateLimitPolicy.PICKUP_VERIFY_STORE, storeId.toString())
        val hash = codec.hash(codec.normalize(input.orEmpty()))
        // Counters commit before the read transaction, without holding a connection while REQUIRES_NEW runs.
        return reads.execute {
            val o = orders.findByStoreIdAndPickupCodeHash(storeId, hash)?.takeIf { usable(it) } ?: notFound()
            val boxes = allocations.findAllByOrderIdAndStatus(o.id!!, AllocationStatus.CONFIRMED).map {
                AllocatedBoxView(it.box.id!!, it.box.boxCode, it.box.totalListedValue.setScale(0), it.box.status.name, it.status.name,
                    it.reservedAt, it.releasedAt, it.releaseReason)
            }
            PickupVerificationView(o.id!!, o.orderCode, o.status.name, storeId, o.quantity, o.pickupAddressSnapshot,
                o.pickupStartsAt, o.pickupEndsAt, boxes, money.money(o))
        }
    }

    private fun usable(o: CustomerOrder) = o.status == OrderStatus.CONFIRMED && clock.instant().isBefore(o.pickupEndsAt)
    private fun notFound(): Nothing = throw ApiException(404, "NOT_FOUND", "Pickup code or order not found")
    private fun unavailable(): Nothing = throw ApiException(503, "SERVICE_UNAVAILABLE", "Pickup code is temporarily unavailable")
}
