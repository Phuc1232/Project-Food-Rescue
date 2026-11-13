// AC-01,04,10,12: StoreRepository responsibility.
package com.foodrescue.backend.store.repository

import com.foodrescue.backend.store.entity.Store
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface StoreRepository : JpaRepository<Store, UUID> {
    @Query("select s from Store s where s.owner.id = :ownerId and s.status <> com.foodrescue.backend.store.StoreStatus.CLOSED")
    fun findOpenByOwner(@Param("ownerId") ownerId: UUID): Store?

    @Query("""select count(s) > 0 from Store s where s.owner.id = :ownerId and s.id <> :storeId
        and s.status <> com.foodrescue.backend.store.StoreStatus.CLOSED""")
    fun existsOtherOpenStore(@Param("ownerId") ownerId: UUID, @Param("storeId") storeId: UUID): Boolean

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Store s where s.id = :id")
    fun findForUpdate(@Param("id") id: UUID): Store?
}
