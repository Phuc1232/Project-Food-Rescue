package com.foodrescue.backend.finance.repository

import com.foodrescue.backend.finance.entity.StorePayout
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface StorePayoutRepository : JpaRepository<StorePayout, UUID>
