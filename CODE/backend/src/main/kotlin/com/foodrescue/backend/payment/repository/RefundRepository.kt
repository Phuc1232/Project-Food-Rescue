// AC-02..06 (T6.5): RefundRepository responsibility.
package com.foodrescue.backend.payment.repository

import com.foodrescue.backend.payment.entity.Refund
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.math.BigDecimal
import java.util.UUID

interface RefundRepository : JpaRepository<Refund, UUID> {
    fun findAllByRequestIdOrderByCreatedAt(requestId: UUID): List<Refund>
    fun existsByProviderRefundId(providerRefundId: String): Boolean
    fun countByRequestId(requestId: UUID): Long

    @Query("select coalesce(sum(r.amount), 0) from Refund r where r.originalPayment.id = :paymentId and r.status = com.foodrescue.backend.payment.RefundStatus.SUCCEEDED")
    fun refundedForPayment(@Param("paymentId") paymentId: UUID): BigDecimal

    @Query("select coalesce(sum(r.amount), 0) from Refund r where r.order.id = :orderId and r.status = com.foodrescue.backend.payment.RefundStatus.SUCCEEDED")
    fun refundedForOrder(@Param("orderId") orderId: UUID): BigDecimal
}
