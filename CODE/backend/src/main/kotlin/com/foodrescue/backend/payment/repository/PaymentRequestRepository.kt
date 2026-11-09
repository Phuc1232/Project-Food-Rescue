package com.foodrescue.backend.payment.repository

import com.foodrescue.backend.payment.entity.PaymentRequest
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PaymentRequestRepository : JpaRepository<PaymentRequest, UUID>
