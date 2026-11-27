package com.foodrescue.backend.notification.repository

import com.foodrescue.backend.notification.entity.Notification
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface NotificationRepository : JpaRepository<Notification, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Notification> {
    fun findByDedupeKey(dedupeKey: String): Notification?
    fun countByRecipientIdAndReadAtIsNull(recipientId: UUID): Long
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select n from Notification n where n.id = :id")
    fun findForUpdate(id: UUID): Notification?
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select n from Notification n where n.recipient.id = :recipientId and n.readAt is null order by n.id")
    fun findUnreadForUpdate(recipientId: UUID): List<Notification>
}
