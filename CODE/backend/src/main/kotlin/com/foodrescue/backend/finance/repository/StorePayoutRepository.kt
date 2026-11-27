package com.foodrescue.backend.finance.repository

import com.foodrescue.backend.finance.entity.StorePayout
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface StorePayoutRepository : JpaRepository<StorePayout, UUID> {
    fun findAllBySettlementIdOrderByCreatedAtAscIdAsc(settlementId: UUID): List<StorePayout>
    fun existsByTransferReference(transferReference: String): Boolean
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from StorePayout p where p.id=:id")
    fun findForUpdate(id: UUID): StorePayout?
}
