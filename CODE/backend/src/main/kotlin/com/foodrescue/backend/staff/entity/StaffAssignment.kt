package com.foodrescue.backend.staff.entity

import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.common.persistence.MutableEntity
import com.foodrescue.backend.staff.AssignmentStatus
import com.foodrescue.backend.store.entity.Store
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "staff_assignment")
class StaffAssignment(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "account_id", nullable = false)
    var account: Account,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "store_id", nullable = false)
    var store: Store,

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "status", nullable = false, length = 50)
    var status: AssignmentStatus,

    @field:Column(name = "assigned_at", nullable = false)
    var assignedAt: Instant,

    @field:Column(name = "revoked_at", nullable = true)
    var revokedAt: Instant? = null
) : MutableEntity()
