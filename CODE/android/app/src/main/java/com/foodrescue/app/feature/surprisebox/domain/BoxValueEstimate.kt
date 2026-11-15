package com.foodrescue.app.feature.surprisebox.domain

import com.foodrescue.app.feature.menu.domain.MenuItem
import com.foodrescue.app.feature.menu.domain.MenuStatus
import com.foodrescue.app.feature.menu.domain.MenuUnit
import java.math.BigDecimal
import java.math.RoundingMode

internal data class BoxValueEstimate(val total: Long = 0, val lineValues: Map<String, Long> = emptyMap(), val invalidIds: Set<String> = emptySet()) {
    val canApply: Boolean get() = lineValues.isNotEmpty() && invalidIds.isEmpty() && total in 2..999_999_999
}

internal fun estimateBoxValue(items: List<MenuItem>, quantities: Map<String, String>): BoxValueEstimate {
    val byId = items.associateBy { it.id }
    val values = mutableMapOf<String, Long>()
    val invalid = mutableSetOf<String>()
    for ((id, input) in quantities) {
        val raw = input.trim().replace(',', '.')
        if (raw.isBlank()) continue
        val item = byId[id]
        val pattern = if (item?.unit == MenuUnit.KILOGRAM) Regex("[0-9]+(?:\\.[0-9]{1,3})?") else Regex("[0-9]+")
        val quantity = raw.takeIf { it.length <= 12 && pattern.matches(it) }?.toBigDecimalOrNull()
        if (quantity?.signum() == 0) continue
        if (item == null || item.status != MenuStatus.ACTIVE || item.price !in 1..999_999_999 ||
            quantity == null || quantity <= BigDecimal.ZERO || quantity > BigDecimal(9999)) {
            invalid += id
            continue
        }
        values[id] = quantity.multiply(BigDecimal(item.price)).setScale(0, RoundingMode.HALF_UP).longValueExact()
    }
    return BoxValueEstimate(values.values.sum(), values, invalid)
}
