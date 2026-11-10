// AC-19: AuthAuditService responsibility.
package com.foodrescue.backend.audit

import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.audit.entity.AuditLog
import com.foodrescue.backend.audit.repository.AuditLogRepository
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.UUID
import org.slf4j.MDC
@Service
class AuthAuditService(private val logs: AuditLogRepository,private val clock: Clock) {
    fun record(account: Account,action: String,entityId: UUID = account.id!!) {
        logs.save(AuditLog(actor=account,entityType="AUTH",entityId=entityId,action=action,occurredAt=clock.instant(),correlationId=MDC.get("requestId")))
    }
}
