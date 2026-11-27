// AC-01..08 (T8.2): NotificationService responsibility.
package com.foodrescue.backend.notification.service
import com.foodrescue.backend.notification.*
import com.foodrescue.backend.notification.entity.Notification
import com.foodrescue.backend.notification.repository.NotificationRepository
import com.foodrescue.backend.notification.dto.*
import com.foodrescue.backend.common.response.PageResponse
import com.foodrescue.backend.common.validation.PageQuery
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.pickup.service.NoShowCompensationService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.data.jpa.domain.Specification
import java.time.Clock
import java.time.Instant
import java.util.UUID
@Service
class NotificationService(private val notifications: NotificationRepository,private val views: NotificationViews,
    private val noShow: NoShowCompensationService,private val clock: Clock) {
    @Transactional(readOnly=true)
    fun myList(accountId: UUID,type: NotificationType?,status: NotificationStatus?,unreadOnly: Boolean?,page: Int,size: Int): PageResponse<NotificationView> {
        val result=notifications.findAll(Specification { root,_,cb -> cb.and(*listOfNotNull(
            cb.equal(root.get<Any>("recipient").get<UUID>("id"),accountId),type?.let { cb.equal(root.get<NotificationType>("type"),it) },
            status?.let { cb.equal(root.get<NotificationStatus>("status"),it) },if(unreadOnly==true) cb.isNull(root.get<Instant>("readAt")) else null).toTypedArray()) },PageQuery().pageable(page,size,"createdAt"))
        return PageResponse(result.content.map(views::view),page,size,result.totalElements,result.totalPages)
    }
    @Transactional(readOnly=true)
    fun unreadCount(accountId: UUID)=UnreadNotificationCountView(notifications.countByRecipientIdAndReadAtIsNull(accountId))
    @Transactional(readOnly=true)
    fun myDetail(accountId: UUID,id: UUID)=views.view(owned(notifications.findById(id).orElse(null),accountId))
    @Transactional
    fun markRead(accountId: UUID,id: UUID): NotificationView {
        val n=owned(notifications.findForUpdate(id),accountId);read(n,clock.instant());return views.view(n)
    }
    @Transactional
    fun markAllRead(accountId: UUID): NotificationReadAllView {
        val rows=notifications.findUnreadForUpdate(accountId);val now=clock.instant();rows.forEach { read(it,now) }
        return NotificationReadAllView(rows.size)
    }
    private fun owned(n: Notification?,accountId: UUID)=n?.takeIf { it.recipient.id==accountId } ?: throw ApiException(404,"NOT_FOUND","Notification not found")
    private fun read(n: Notification,now: Instant) {
        if(n.readAt!=null) return
        n.readAt=now
        if(n.status in listOf(NotificationStatus.PENDING,NotificationStatus.SENT)) {
            n.sentAt=n.sentAt ?: now;n.deliveredAt=n.deliveredAt ?: now;n.status=NotificationStatus.DELIVERED;n.nextAttemptAt=null
        }
        if(n.type==NotificationType.NO_SHOW) noShow.recordNotificationReceived(n.id!!,now)
    }
}
