// AC-01..06 (T6.2): PaymentRequestRepository responsibility.
package com.foodrescue.backend.payment.repository

import com.foodrescue.backend.payment.PaymentRequestStatus
import com.foodrescue.backend.payment.entity.PaymentRequest
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface PaymentRequestRepository : JpaRepository<PaymentRequest, UUID> {
    fun findFirstByOrderIdAndStatusIn(orderId: UUID, statuses: Collection<PaymentRequestStatus>): PaymentRequest?
    fun findFirstByOrderIdOrderByCreatedAtDesc(orderId: UUID): PaymentRequest?
    fun findByProviderAndProviderOrderCode(provider: String, code: Long): PaymentRequest?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PaymentRequest r where r.id = :id")
    fun findForUpdate(@Param("id") id: UUID): PaymentRequest?
}
