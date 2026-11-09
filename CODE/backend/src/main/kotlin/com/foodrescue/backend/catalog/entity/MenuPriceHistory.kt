package com.foodrescue.backend.catalog.entity

import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.catalog.entity.MenuItem
import com.foodrescue.backend.common.persistence.BaseEntity
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import org.hibernate.annotations.Immutable

@Entity
@Table(name = "menu_price_history")
@Immutable
class MenuPriceHistory(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "menu_item_id", nullable = false)
    val menuItem: MenuItem,

    @field:Column(name = "previous_price", nullable = false, precision = 19, scale = 2)
    val previousPrice: BigDecimal,

    @field:Column(name = "new_price", nullable = false, precision = 19, scale = 2)
    val newPrice: BigDecimal,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "changed_by_id", nullable = false)
    val changedBy: Account,

    @field:Column(name = "changed_at", nullable = false)
    val changedAt: Instant,

    @field:Column(name = "reason", nullable = false, columnDefinition = "text")
    val reason: String
) : BaseEntity()
