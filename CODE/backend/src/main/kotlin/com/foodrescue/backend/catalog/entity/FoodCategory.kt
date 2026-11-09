package com.foodrescue.backend.catalog.entity

import com.foodrescue.backend.common.persistence.MutableEntity
import jakarta.persistence.*

@Entity
@Table(name = "food_category")
class FoodCategory(
    @field:Column(name = "code", nullable = false, length = 255)
    var code: String,

    @field:Column(name = "name", nullable = false, length = 255)
    var name: String,

    @field:Column(name = "active", nullable = false)
    var active: Boolean
) : MutableEntity()
