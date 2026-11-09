package com.foodrescue.backend.finance.repository

import com.foodrescue.backend.finance.entity.FinancialPolicy
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FinancialPolicyRepository : JpaRepository<FinancialPolicy, UUID>
