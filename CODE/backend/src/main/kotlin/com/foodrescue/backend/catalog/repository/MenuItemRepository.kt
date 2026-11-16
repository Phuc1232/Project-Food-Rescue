// AC-01..08 (T4.1): MenuItemRepository responsibility.
package com.foodrescue.backend.catalog.repository

import com.foodrescue.backend.catalog.entity.MenuItem
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface MenuItemRepository : JpaRepository<MenuItem, UUID> {
    fun findAllByStoreIdOrderByName(storeId: UUID): List<MenuItem>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MenuItem m where m.id = :id")
    fun findForUpdate(@Param("id") id: UUID): MenuItem?
}
