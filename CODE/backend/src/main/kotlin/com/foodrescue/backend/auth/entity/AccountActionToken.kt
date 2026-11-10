// AC-14,18,21,23: AccountActionToken responsibility.
package com.foodrescue.backend.auth.entity

import com.foodrescue.backend.common.persistence.BaseEntity
import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.auth.TokenPurpose
import jakarta.persistence.*
import java.time.Instant
@Entity
@Table(name="account_action_token")
class AccountActionToken(
    @field:ManyToOne(fetch=FetchType.LAZY,optional=false) @field:JoinColumn(name="account_id") val account: Account,
    @field:Enumerated(EnumType.STRING) @field:Column(nullable=false,length=32) val purpose: TokenPurpose,
    @field:Column(nullable=false,length=64) val tokenHash: String,
    @field:Column(nullable=false) val expiresAt: Instant,
    var consumedAt: Instant? = null
) : BaseEntity()
