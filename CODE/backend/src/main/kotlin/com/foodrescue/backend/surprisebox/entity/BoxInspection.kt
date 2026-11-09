package com.foodrescue.backend.surprisebox.entity

import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.common.persistence.BaseEntity
import com.foodrescue.backend.surprisebox.InspectionResult
import com.foodrescue.backend.surprisebox.entity.PhysicalBox
import jakarta.persistence.*
import java.time.Instant
import org.hibernate.annotations.Immutable

@Entity
@Table(name = "box_inspection")
@Immutable
class BoxInspection(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "box_id", nullable = false)
    val box: PhysicalBox,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "inspector_id", nullable = false)
    val inspector: Account,

    @field:Column(name = "inspected_at", nullable = false)
    val inspectedAt: Instant,

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "result", nullable = false, length = 50)
    val result: InspectionResult,

    @field:Column(name = "safe_until", nullable = false)
    val safeUntil: Instant,

    @field:Column(name = "notes", nullable = false, columnDefinition = "text")
    val notes: String
) : BaseEntity()
