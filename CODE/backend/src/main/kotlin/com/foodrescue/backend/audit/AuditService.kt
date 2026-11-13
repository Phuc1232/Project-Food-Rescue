// AC-14 (T3.2): AuditService responsibility.
package com.foodrescue.backend.audit

import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.audit.entity.AuditLog
import com.foodrescue.backend.audit.repository.AuditLogRepository
import com.foodrescue.backend.store.entity.Store
import org.slf4j.MDC
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.UUID

/** General business audit (T3.2 Q7). Call inside the transaction that performs the audited change. */
@Service
class AuditService(private val logs: AuditLogRepository, private val clock: Clock) {
    fun record(actor: Account, entityType: String, entityId: UUID, action: String, store: Store? = null, afterState: String? = null) {
        logs.save(AuditLog(actor = actor, store = store, entityType = entityType, entityId = entityId, action = action,
            afterState = afterState, correlationId = MDC.get("requestId"), occurredAt = clock.instant()))
    }
}
