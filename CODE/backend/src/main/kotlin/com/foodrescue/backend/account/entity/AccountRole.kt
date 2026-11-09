package com.foodrescue.backend.account.entity

import com.foodrescue.backend.account.PlatformRole
import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.common.persistence.MutableEntity
import jakarta.persistence.*

@Entity
@Table(name = "account_role")
class AccountRole(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "account_id", nullable = false)
    var account: Account,

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "role", nullable = false, length = 50)
    var role: PlatformRole
) : MutableEntity()
