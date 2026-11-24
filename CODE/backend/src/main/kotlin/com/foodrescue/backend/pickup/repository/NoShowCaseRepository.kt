// AC-01..14 (T7.4): NoShowCaseRepository responsibility.
package com.foodrescue.backend.pickup.repository
import com.foodrescue.backend.pickup.entity.NoShowCase
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.*
import java.util.UUID
interface NoShowCaseRepository : JpaRepository<NoShowCase, UUID> {
    fun findByOrderId(orderId: UUID): NoShowCase?
    @Query("select n.id from NoShowCase n where n.notification.id = :notificationId")
    fun findIdByNotificationId(notificationId: UUID): UUID?
    fun findAllByOrderStoreIdOrderByVerifiedAtDesc(storeId: UUID): List<NoShowCase>
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from NoShowCase n where n.id = :id")
    fun findForUpdate(id: UUID): NoShowCase?
}
