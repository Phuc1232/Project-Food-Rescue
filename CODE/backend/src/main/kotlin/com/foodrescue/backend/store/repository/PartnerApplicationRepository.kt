// AC-02,07,11,13: PartnerApplicationRepository responsibility.
package com.foodrescue.backend.store.repository

import com.foodrescue.backend.store.ApplicationStatus
import com.foodrescue.backend.store.entity.PartnerApplication
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface PartnerApplicationRepository : JpaRepository<PartnerApplication, UUID> {
    fun findTopByStoreIdOrderByRevisionDesc(storeId: UUID): PartnerApplication?
    fun findAllByStoreIdOrderByRevisionDesc(storeId: UUID): List<PartnerApplication>
    fun findAllByStatus(status: ApplicationStatus, pageable: Pageable): Page<PartnerApplication>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PartnerApplication a where a.id = :id")
    fun findForUpdate(@Param("id") id: UUID): PartnerApplication?
}
