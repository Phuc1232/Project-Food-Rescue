// AC-09,21,22: RefreshToken responsibility.
package com.foodrescue.backend.auth.entity

import com.foodrescue.backend.common.persistence.BaseEntity
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID
@Entity
@Table(name="refresh_token")
class RefreshToken(
    @field:ManyToOne(fetch=FetchType.LAZY,optional=false) @field:JoinColumn(name="session_id") val session: LoginSession,
    @field:Column(nullable=false,length=64) val tokenHash: String,
    @field:Column(nullable=false) val expiresAt: Instant,
    var consumedAt: Instant? = null,
    var replacedById: UUID? = null
) : BaseEntity()
