package com.foodrescue.backend.surprisebox.entity

import com.foodrescue.backend.common.persistence.MutableEntity
import com.foodrescue.backend.store.entity.Store
import com.foodrescue.backend.surprisebox.SaleBatchStatus
import com.foodrescue.backend.surprisebox.entity.BoxTemplate
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "sale_batch")
class SaleBatch(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "store_id", nullable = false)
    var store: Store,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "template_id", nullable = false)
    var template: BoxTemplate,

    @field:Column(name = "sale_date", nullable = false)
    var saleDate: LocalDate,

    @field:Column(name = "selling_price", nullable = false, precision = 19, scale = 2)
    var sellingPrice: BigDecimal,

    @field:Column(name = "minimum_value", nullable = false, precision = 19, scale = 2)
    var minimumValue: BigDecimal,

    @field:Column(name = "order_deadline", nullable = false)
    var orderDeadline: Instant,

    @field:Column(name = "pickup_starts_at", nullable = false)
    var pickupStartsAt: Instant,

    @field:Column(name = "pickup_ends_at", nullable = false)
    var pickupEndsAt: Instant,

    @field:Column(name = "safety_warnings", nullable = false, columnDefinition = "text")
    var safetyWarnings: String,

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "status", nullable = false, length = 50)
    var status: SaleBatchStatus
) : MutableEntity()
