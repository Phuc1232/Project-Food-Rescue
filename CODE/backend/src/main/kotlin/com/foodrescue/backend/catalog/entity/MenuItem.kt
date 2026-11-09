package com.foodrescue.backend.catalog.entity

import com.foodrescue.backend.catalog.MenuItemStatus
import com.foodrescue.backend.catalog.entity.FoodCategory
import com.foodrescue.backend.common.persistence.MutableEntity
import com.foodrescue.backend.store.entity.Store
import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "menu_item")
class MenuItem(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "store_id", nullable = false)
    var store: Store,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "category_id", nullable = false)
    var category: FoodCategory,

    @field:Column(name = "name", nullable = false, length = 255)
    var name: String,

    @field:Column(name = "listed_price", nullable = false, precision = 19, scale = 2)
    var listedPrice: BigDecimal,

    @field:Column(name = "ingredients", nullable = false, columnDefinition = "text")
    var ingredients: String,

    @field:Column(name = "safety_warnings", nullable = false, columnDefinition = "text")
    var safetyWarnings: String,

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "status", nullable = false, length = 50)
    var status: MenuItemStatus
) : MutableEntity()
