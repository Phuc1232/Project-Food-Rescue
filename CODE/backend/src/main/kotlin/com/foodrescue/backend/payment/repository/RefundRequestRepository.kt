// AC-01,02,04 (T6.4): RefundRequestRepository responsibility.
package com.foodrescue.backend.payment.repository

import com.foodrescue.backend.payment.entity.RefundRequest
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RefundRequestRepository : JpaRepository<RefundRequest, UUID> {
    @org.springframework.data.jpa.repository.Query("select r.order.id from RefundRequest r where r.id = :id")
    fun findOrderId(id: UUID): UUID?
    fun findAllByComplaintIdOrderByCreatedAt(complaintId: UUID): List<RefundRequest>
    @org.springframework.data.jpa.repository.Query("select coalesce(sum(coalesce(r.approvedAmount,r.requestedAmount)),0) from RefundRequest r where r.payment.id = :paymentId and r.status in (com.foodrescue.backend.payment.RefundRequestStatus.PENDING, com.foodrescue.backend.payment.RefundRequestStatus.APPROVED)")
    fun openObligationsForPayment(paymentId: UUID): java.math.BigDecimal
    fun existsByPaymentId(paymentId: UUID): Boolean
    fun findByPaymentId(paymentId: UUID): RefundRequest?
    fun findAllByOrderIdOrderByCreatedAt(orderId: UUID): List<RefundRequest>
    fun findAllByStatus(status: com.foodrescue.backend.payment.RefundRequestStatus, pageable: org.springframework.data.domain.Pageable): org.springframework.data.domain.Page<RefundRequest>

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from RefundRequest r where r.id = :id")
    fun findForUpdate(@org.springframework.data.repository.query.Param("id") id: UUID): RefundRequest?
}
