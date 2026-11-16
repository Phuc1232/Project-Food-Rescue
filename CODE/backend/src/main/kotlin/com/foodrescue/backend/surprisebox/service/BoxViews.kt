// AC-01,08 (T4.4): BoxViews responsibility.
package com.foodrescue.backend.surprisebox.service

import com.foodrescue.backend.surprisebox.dto.*
import com.foodrescue.backend.surprisebox.entity.PhysicalBox
import com.foodrescue.backend.surprisebox.repository.BoxInspectionRepository
import com.foodrescue.backend.surprisebox.repository.BoxItemRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.util.UUID

/** Must run inside a transaction. */
@Component
class BoxViews(private val items: BoxItemRepository, private val inspections: BoxInspectionRepository, private val jdbc: JdbcTemplate) {
    fun box(b: PhysicalBox) = BoxView(b.id!!, b.boxCode, b.currentBatch.id!!, b.status.name, b.totalListedValue.setScale(0), b.safeUntil, b.packedAt,
        b.packedBy.id!!, b.totalListedValue >= b.currentBatch.minimumValue && !b.safeUntil.isBefore(b.currentBatch.pickupEndsAt))

    fun detail(b: PhysicalBox) = BoxDetail(box(b), b.storageInstructions, b.safetyWarnings,
        items.findAllByBoxIdOrderByItemNameSnapshot(b.id!!).map { BoxItemView(it.menuItem.id!!, it.itemNameSnapshot, it.quantity,
            it.unitPriceSnapshot.setScale(0), it.safetyWarningsSnapshot, it.expiresAt) },
        inspections.findAllByBoxIdOrderByInspectedAtDesc(b.id!!).map { BoxInspectionView(it.result.name, it.inspector.id!!, it.inspectedAt, it.safeUntil, it.notes) },
        jdbc.queryForList("SELECT order_id FROM box_allocation WHERE box_id = ? AND status IN ('HELD','CONFIRMED','DELIVERED') ORDER BY reserved_at DESC LIMIT 1",
            UUID::class.java, b.id).firstOrNull())
}
