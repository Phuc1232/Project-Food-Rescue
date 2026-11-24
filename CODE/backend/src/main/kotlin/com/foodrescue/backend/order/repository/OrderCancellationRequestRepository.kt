// AC-01..15 (T7.3): OrderCancellationRequestRepository responsibility.
package com.foodrescue.backend.order.repository

import com.foodrescue.backend.order.entity.OrderCancellationRequest
import com.foodrescue.backend.order.CancellationRequestStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.util.UUID

interface OrderCancellationRequestRepository : JpaRepository<OrderCancellationRequest, UUID> {
    fun findByOrderIdAndIdempotencyKey(orderId: UUID, key: String): OrderCancellationRequest?
    fun findAllByOrderIdOrderByRequestedAtDesc(orderId: UUID): List<OrderCancellationRequest>
    fun findByOrderIdAndStatusIn(orderId: UUID, statuses: Collection<CancellationRequestStatus>): OrderCancellationRequest?
    fun findAllByStatus(status: CancellationRequestStatus, pageable: Pageable): Page<OrderCancellationRequest>
    @Query("select r.order.id from OrderCancellationRequest r where r.id = :id")
    fun findOrderId(id: UUID): UUID?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from OrderCancellationRequest r where r.id = :id")
    fun findForUpdate(id: UUID): OrderCancellationRequest?
}
