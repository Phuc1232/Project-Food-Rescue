// AC-01..09 (T4.3): SaleBatchRepository responsibility.
package com.foodrescue.backend.surprisebox.repository

import com.foodrescue.backend.surprisebox.entity.SaleBatch
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface SaleBatchRepository : JpaRepository<SaleBatch, UUID> {
    fun findAllByStoreIdOrderByPickupStartsAtDesc(storeId: UUID): List<SaleBatch>

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select b from SaleBatch b where b.id = :id")
    fun findForShare(id: UUID): SaleBatch?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from SaleBatch b where b.store.id = :storeId order by b.id")
    fun lockAllByStoreId(storeId: UUID): List<SaleBatch>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from SaleBatch b where b.id = :id")
    fun findForUpdate(@Param("id") id: UUID): SaleBatch?
}
