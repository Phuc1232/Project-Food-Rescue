package com.foodrescue.backend.staff.entity

import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.common.persistence.MutableEntity
import com.foodrescue.backend.staff.entity.Permission
import com.foodrescue.backend.staff.entity.StaffAssignment
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "staff_permission")
class StaffPermission(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "assignment_id", nullable = false)
    var assignment: StaffAssignment,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "permission_id", nullable = false)
    var permission: Permission,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "granted_by_id", nullable = false)
    var grantedBy: Account,

    @field:Column(name = "granted_at", nullable = false)
    var grantedAt: Instant
) : MutableEntity()
