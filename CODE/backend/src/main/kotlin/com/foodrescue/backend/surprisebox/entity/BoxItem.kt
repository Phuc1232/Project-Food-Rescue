package com.foodrescue.backend.surprisebox.entity

import com.foodrescue.backend.catalog.entity.MenuItem
import com.foodrescue.backend.common.persistence.BaseEntity
import com.foodrescue.backend.store.entity.Store
import com.foodrescue.backend.surprisebox.entity.PhysicalBox
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import org.hibernate.annotations.Immutable

@Entity
@Table(name = "box_item")
@Immutable
class BoxItem(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "store_id", nullable = false)
    val store: Store,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "box_id", nullable = false)
    val box: PhysicalBox,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "menu_item_id", nullable = false)
    val menuItem: MenuItem,

    @field:Column(name = "quantity", nullable = false)
    val quantity: Int,

    @field:Column(name = "item_name_snapshot", nullable = false, length = 255)
    val itemNameSnapshot: String,

    @field:Column(name = "unit_price_snapshot", nullable = false, precision = 19, scale = 2)
    val unitPriceSnapshot: BigDecimal,

    @field:Column(name = "safety_warnings_snapshot", nullable = false, columnDefinition = "text")
    val safetyWarningsSnapshot: String,

    @field:Column(name = "expires_at", nullable = false)
    val expiresAt: Instant
) : BaseEntity()
