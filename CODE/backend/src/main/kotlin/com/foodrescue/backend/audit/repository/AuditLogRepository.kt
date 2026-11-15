// AC-13 (T3.3): AuditLogRepository responsibility.
package com.foodrescue.backend.audit.repository

import com.foodrescue.backend.audit.entity.AuditLog
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AuditLogRepository : JpaRepository<AuditLog, UUID> {
    fun findAllByStoreIdAndEntityTypeIn(storeId: UUID, entityTypes: Collection<String>, pageable: Pageable): Page<AuditLog>
}
