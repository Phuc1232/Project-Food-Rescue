package com.foodrescue.backend.surprisebox.repository

import com.foodrescue.backend.surprisebox.entity.PhysicalBox
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface PhysicalBoxRepository : JpaRepository<PhysicalBox, UUID> {
    /** Must be called inside the same transaction as the dependent validation and writes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select entity from PhysicalBox entity where entity.id = :id")
    fun findForUpdate(@Param("id") id: UUID): PhysicalBox?
}
