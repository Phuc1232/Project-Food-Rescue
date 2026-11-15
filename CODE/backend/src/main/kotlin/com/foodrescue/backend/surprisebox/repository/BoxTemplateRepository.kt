// AC-01..06 (T4.2): BoxTemplateRepository responsibility.
package com.foodrescue.backend.surprisebox.repository

import com.foodrescue.backend.surprisebox.entity.BoxTemplate
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface BoxTemplateRepository : JpaRepository<BoxTemplate, UUID> {
    fun findAllByStoreIdOrderByName(storeId: UUID): List<BoxTemplate>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from BoxTemplate t where t.id = :id")
    fun findForUpdate(@Param("id") id: UUID): BoxTemplate?
}
