// AC-01,08: StoreOpeningHourRepository responsibility.
package com.foodrescue.backend.store.repository

import com.foodrescue.backend.store.entity.StoreOpeningHour
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface StoreOpeningHourRepository : JpaRepository<StoreOpeningHour, UUID> {
    fun findAllByStoreIdOrderByDayOfWeekAscShiftNumberAsc(storeId: UUID): List<StoreOpeningHour>

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from StoreOpeningHour h where h.store.id = :storeId")
    fun deleteAllByStoreId(@Param("storeId") storeId: UUID)
}
