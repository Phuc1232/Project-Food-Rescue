// AC-05,06,07: StoreChangeRequestRepository responsibility.
package com.foodrescue.backend.store.repository

import com.foodrescue.backend.store.StoreChangeRequestStatus
import com.foodrescue.backend.store.entity.StoreChangeRequest
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface StoreChangeRequestRepository : JpaRepository<StoreChangeRequest, UUID> {
    fun findFirstByStoreIdAndStatus(storeId: UUID, status: StoreChangeRequestStatus): StoreChangeRequest?
    fun findAllByStoreIdOrderByRequestedAtDesc(storeId: UUID): List<StoreChangeRequest>
    fun findAllByStatus(status: StoreChangeRequestStatus, pageable: Pageable): Page<StoreChangeRequest>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from StoreChangeRequest r where r.id = :id")
    fun findForUpdate(@Param("id") id: UUID): StoreChangeRequest?
}
