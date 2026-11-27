package com.foodrescue.backend.finance.repository

import com.foodrescue.backend.finance.entity.StorePayoutAccount
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface StorePayoutAccountRepository : JpaRepository<StorePayoutAccount, UUID> {
    fun findFirstByStoreIdAndEffectiveUntilIsNull(storeId: UUID): StorePayoutAccount?
    fun findAllByStoreIdOrderByAccountVersionDesc(storeId: UUID): List<StorePayoutAccount>
    fun findByIdAndStoreId(id: UUID, storeId: UUID): StorePayoutAccount?
    @org.springframework.data.jpa.repository.Query("select coalesce(max(a.accountVersion),0) from StorePayoutAccount a where a.store.id=:storeId")
    fun findMaxVersion(storeId: UUID): Int
}
