package com.foodrescue.backend.staff.entity

import com.foodrescue.backend.common.persistence.MutableEntity
import jakarta.persistence.*

@Entity
@Table(name = "permission")
class Permission(
    @field:Column(name = "code", nullable = false, length = 255)
    var code: String,

    @field:Column(name = "description", nullable = false, columnDefinition = "text")
    var description: String
) : MutableEntity()
