package com.foodrescue.backend.finance.repository

import com.foodrescue.backend.finance.entity.StorePayoutAccount
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface StorePayoutAccountRepository : JpaRepository<StorePayoutAccount, UUID>
