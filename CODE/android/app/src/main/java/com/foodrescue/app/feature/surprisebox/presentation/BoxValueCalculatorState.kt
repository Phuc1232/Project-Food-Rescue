package com.foodrescue.app.feature.surprisebox.presentation

import com.foodrescue.app.core.util.searchKey
import com.foodrescue.app.feature.menu.domain.MenuStatus
import com.foodrescue.app.feature.menu.domain.StoreMenu
import com.foodrescue.app.feature.surprisebox.domain.estimateBoxValue

internal data class BoxValueCalculatorState(
    val menu: StoreMenu = StoreMenu(),
    val quantities: Map<String, String> = emptyMap(),
    val query: String = "",
    val loading: Boolean = true,
    val error: String? = null,
    val notice: String? = null,
) {
    val estimate get() = estimateBoxValue(menu.items, quantities)
    val canApply get() = !loading && error == null && estimate.canApply
    val visibleItems get() = menu.items.filter {
        (it.status == MenuStatus.ACTIVE || it.id in quantities) && it.name.searchKey().contains(query.searchKey())
    }.sortedBy { it.name.searchKey() }
}
