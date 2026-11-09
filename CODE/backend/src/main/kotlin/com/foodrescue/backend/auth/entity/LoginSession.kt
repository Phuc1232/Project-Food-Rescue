package com.foodrescue.backend.auth.entity

import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.common.persistence.MutableEntity
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "login_session")
class LoginSession(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "account_id", nullable = false)
    var account: Account,

    @field:Column(name = "token_hash", nullable = false, length = 255)
    var tokenHash: String,

    @field:Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    @field:Column(name = "revoked_at", nullable = true)
    var revokedAt: Instant? = null
) : MutableEntity()
