// AC-01..07 (T8.5): AdminAuditLogController responsibility.
package com.foodrescue.backend.audit.controller
import com.foodrescue.backend.audit.service.AdminActivityLogService
import org.springframework.web.bind.annotation.*
import org.springframework.security.access.prepost.PreAuthorize
import java.time.Instant
import java.util.UUID
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/v1/admin/audit-logs")
class AdminAuditLogController(private val service: AdminActivityLogService) {
    @GetMapping fun list(@RequestParam(required=false) storeId: UUID?,@RequestParam(required=false) entityType: String?,@RequestParam(required=false) entityId: UUID?,@RequestParam(required=false) actorId: UUID?,@RequestParam(required=false) action: String?,
        @RequestParam(required=false) from: Instant?,@RequestParam(required=false) to: Instant?,@RequestParam(defaultValue="0") page: Int,@RequestParam(defaultValue="20") size: Int,@RequestParam(defaultValue="occurredAt") sort: String)=service.list(storeId,entityType,entityId,actorId,action,from,to,page,size,sort)
}
