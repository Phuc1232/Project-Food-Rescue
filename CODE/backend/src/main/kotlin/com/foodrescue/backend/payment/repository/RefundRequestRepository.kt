// AC-01,02,04 (T6.4): RefundRequestRepository responsibility.
package com.foodrescue.backend.payment.repository

import com.foodrescue.backend.payment.entity.RefundRequest
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RefundRequestRepository : JpaRepository<RefundRequest, UUID> {
    fun existsByPaymentId(paymentId: UUID): Boolean
    fun findByPaymentId(paymentId: UUID): RefundRequest?
    fun findAllByOrderIdOrderByCreatedAt(orderId: UUID): List<RefundRequest>
}
