package com.foodrescue.backend.surprisebox.entity

import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.common.persistence.MutableEntity
import com.foodrescue.backend.store.entity.Store
import com.foodrescue.backend.surprisebox.BoxStatus
import com.foodrescue.backend.surprisebox.entity.SaleBatch
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "physical_box")
class PhysicalBox(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "store_id", nullable = false)
    var store: Store,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "current_batch_id", nullable = false)
    var currentBatch: SaleBatch,

    @field:Column(name = "box_code", nullable = false, length = 255)
    var boxCode: String,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "packed_by_id", nullable = false)
    var packedBy: Account,

    @field:Column(name = "packed_at", nullable = false)
    var packedAt: Instant,

    @field:Column(name = "total_listed_value", nullable = false, precision = 19, scale = 2)
    var totalListedValue: BigDecimal,

    @field:Column(name = "safe_until", nullable = false)
    var safeUntil: Instant,

    @field:Column(name = "storage_instructions", nullable = false, columnDefinition = "text")
    var storageInstructions: String,

    @field:Column(name = "safety_warnings", nullable = false, columnDefinition = "text")
    var safetyWarnings: String,

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "status", nullable = false, length = 50)
    var status: BoxStatus
) : MutableEntity()
