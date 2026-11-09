package com.foodrescue.backend.account.entity

import com.foodrescue.backend.account.AccountStatus
import com.foodrescue.backend.common.persistence.MutableEntity
import jakarta.persistence.*

@Entity
@Table(name = "account")
class Account(
    @field:Column(name = "login_identifier", nullable = false, length = 255)
    var loginIdentifier: String,

    @field:Column(name = "password_hash", nullable = false, length = 255)
    var passwordHash: String,

    @field:Column(name = "display_name", nullable = false, length = 255)
    var displayName: String,

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "status", nullable = false, length = 50)
    var status: AccountStatus
) : MutableEntity()
