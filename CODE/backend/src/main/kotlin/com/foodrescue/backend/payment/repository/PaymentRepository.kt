package com.foodrescue.backend.payment.repository

import com.foodrescue.backend.payment.entity.Payment
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface PaymentRepository : JpaRepository<Payment, UUID> {
    /** Must be called inside the same transaction as the dependent validation and writes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select entity from Payment entity where entity.id = :id")
    fun findForUpdate(@Param("id") id: UUID): Payment?
}
