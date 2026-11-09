package com.foodrescue.backend.payment.repository

import com.foodrescue.backend.payment.entity.RefundRequest
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RefundRequestRepository : JpaRepository<RefundRequest, UUID>
