package com.foodrescue.backend.surprisebox.entity

import com.foodrescue.backend.catalog.entity.FoodCategory
import com.foodrescue.backend.common.persistence.MutableEntity
import com.foodrescue.backend.file.entity.StoredFile
import com.foodrescue.backend.store.entity.Store
import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "box_template")
class BoxTemplate(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "store_id", nullable = false)
    var store: Store,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "category_id", nullable = false)
    var category: FoodCategory,

    @field:Column(name = "name", nullable = false, length = 255)
    var name: String,

    @field:Column(name = "description", nullable = false, columnDefinition = "text")
    var description: String,

    @field:Column(name = "safety_warnings", nullable = false, columnDefinition = "text")
    var safetyWarnings: String,

    @field:Column(name = "default_selling_price", nullable = false, precision = 19, scale = 2)
    var defaultSellingPrice: BigDecimal,

    @field:Column(name = "default_minimum_value", nullable = false, precision = 19, scale = 2)
    var defaultMinimumValue: BigDecimal,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = true)
    @field:JoinColumn(name = "cover_file_id", nullable = true)
    var coverFile: StoredFile? = null,

    @field:Column(name = "active", nullable = false)
    var active: Boolean
) : MutableEntity()
