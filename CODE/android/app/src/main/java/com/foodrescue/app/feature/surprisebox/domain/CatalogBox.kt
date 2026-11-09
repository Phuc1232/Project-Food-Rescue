package com.foodrescue.app.feature.surprisebox.domain

import com.foodrescue.app.core.common.PickupLocation

enum class BoxCategory(val label: String) {
    BAKERY("Bánh & đồ ngọt"),
    MEALS("Món ăn chế biến"),
    PRODUCE("Rau củ & trái cây"),
    DRINKS("Đồ uống"),
    MIXED("Thực phẩm tổng hợp"),
}

/** Public catalog metadata only. Actual contents of each Surprise Box are never searchable. */
data class CatalogBox(
    val id: Int, val store: String, val category: BoxCategory, val name: String,
    val price: Int, val minimumValue: Int, val remaining: Int, val pickup: String,
    val description: String, val allergens: String, val district: String,
    val distanceMeters: Int, val pickupStartHour: Int, val initials: String,
    val location: PickupLocation? = null,
    val storeId: Int = id,
) {
    val isAvailable: Boolean get() = remaining > 0
}
