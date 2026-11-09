package com.foodrescue.backend.finance.repository

import com.foodrescue.backend.finance.entity.Settlement
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface SettlementRepository : JpaRepository<Settlement, UUID> {
    /** Must be called inside the same transaction as the dependent validation and writes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select entity from Settlement entity where entity.id = :id")
    fun findForUpdate(@Param("id") id: UUID): Settlement?
}
