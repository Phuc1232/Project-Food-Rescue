// AC-01..08 (T4.3): SaleBatchViews responsibility.
package com.foodrescue.backend.surprisebox.service

import com.foodrescue.backend.surprisebox.dto.SaleBatchView
import com.foodrescue.backend.surprisebox.entity.SaleBatch
import org.springframework.stereotype.Component

@Component
class SaleBatchViews(private val availability: BatchAvailability) {
    fun view(b: SaleBatch) = SaleBatchView(b.id!!, b.template.id!!, b.template.name, b.saleDate, b.sellingPrice.setScale(0),
        b.minimumValue.setScale(0), b.orderDeadline, b.pickupStartsAt, b.pickupEndsAt, b.safetyWarnings, b.status.name, b.statusReason,
        b.version, availability.available(b.id!!))

    fun snapshot(b: SaleBatch): Map<String, Any?> = linkedMapOf("sellingPrice" to b.sellingPrice.setScale(0), "minimumValue" to b.minimumValue.setScale(0),
        "orderDeadline" to b.orderDeadline.toString(), "pickupStartsAt" to b.pickupStartsAt.toString(), "pickupEndsAt" to b.pickupEndsAt.toString(),
        "safetyWarnings" to b.safetyWarnings)
}
