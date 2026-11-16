// AC-08,09 (T4.1): PriceReferenceDocumentRepository responsibility.
package com.foodrescue.backend.catalog.repository

import com.foodrescue.backend.catalog.entity.PriceReferenceDocument
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface PriceReferenceDocumentRepository : JpaRepository<PriceReferenceDocument, UUID> {
    fun findAllByStoreIdOrderByUploadedAtDesc(storeId: UUID): List<PriceReferenceDocument>

    /** Documents for one item plus whole-menu documents. */
    @Query("select d from PriceReferenceDocument d where d.store.id = :storeId and (d.menuItem is null or d.menuItem.id = :itemId) order by d.uploadedAt desc")
    fun findForItem(@Param("storeId") storeId: UUID, @Param("itemId") itemId: UUID): List<PriceReferenceDocument>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from PriceReferenceDocument d where d.id = :id")
    fun findForUpdate(@Param("id") id: UUID): PriceReferenceDocument?
}
