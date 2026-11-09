package com.foodrescue.backend.payment.repository

import com.foodrescue.backend.payment.entity.Refund
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RefundRepository : JpaRepository<Refund, UUID>
