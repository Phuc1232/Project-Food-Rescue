package com.foodrescue.backend.audit.entity

import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.common.persistence.BaseEntity
import com.foodrescue.backend.store.entity.Store
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID
import org.hibernate.annotations.Immutable

@Entity
@Table(name = "audit_log")
@Immutable
class AuditLog(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = true)
    @field:JoinColumn(name = "actor_id", nullable = true)
    val actor: Account? = null,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = true)
    @field:JoinColumn(name = "store_id", nullable = true)
    val store: Store? = null,

    @field:Column(name = "entity_type", nullable = false, length = 255)
    val entityType: String,

    @field:Column(name = "entity_id", nullable = false)
    val entityId: UUID,

    @field:Column(name = "action", nullable = false, length = 255)
    val action: String,

    @field:Column(name = "before_state", nullable = true, columnDefinition = "text")
    val beforeState: String? = null,

    @field:Column(name = "after_state", nullable = true, columnDefinition = "text")
    val afterState: String? = null,

    @field:Column(name = "correlation_id", nullable = true, length = 255)
    val correlationId: String? = null,

    @field:Column(name = "occurred_at", nullable = false)
    val occurredAt: Instant
) : BaseEntity()
