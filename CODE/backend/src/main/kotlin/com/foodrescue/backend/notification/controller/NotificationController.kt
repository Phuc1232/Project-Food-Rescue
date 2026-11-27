// AC-01..08 (T8.2): NotificationController responsibility.
package com.foodrescue.backend.notification.controller
import com.foodrescue.backend.notification.*
import com.foodrescue.backend.notification.service.NotificationService
import com.foodrescue.backend.common.security.CurrentAccount
import org.springframework.web.bind.annotation.*
import org.springframework.security.access.prepost.PreAuthorize
import java.util.UUID
@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("isAuthenticated()")
class NotificationController(private val service: NotificationService,private val current: CurrentAccount) {
    @GetMapping fun list(@RequestParam(required=false) type: NotificationType?,@RequestParam(required=false) status: NotificationStatus?,
        @RequestParam(required=false) unreadOnly: Boolean?,@RequestParam(defaultValue="0") page: Int,@RequestParam(defaultValue="20") size: Int)=service.myList(current.get().accountId,type,status,unreadOnly,page,size)
    @GetMapping("/unread-count") fun unreadCount()=service.unreadCount(current.get().accountId)
    @GetMapping("/{notificationId}") fun detail(@PathVariable notificationId: UUID)=service.myDetail(current.get().accountId,notificationId)
    @PostMapping("/{notificationId}/read") fun read(@PathVariable notificationId: UUID)=service.markRead(current.get().accountId,notificationId)
    @PostMapping("/read-all") fun readAll()=service.markAllRead(current.get().accountId)
}
