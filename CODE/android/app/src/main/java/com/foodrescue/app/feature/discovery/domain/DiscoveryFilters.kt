package com.foodrescue.app.feature.discovery.domain

import com.foodrescue.app.feature.surprisebox.domain.CatalogBox
import com.foodrescue.app.feature.surprisebox.domain.BoxCategory
import com.foodrescue.app.core.util.searchKey
import kotlin.math.roundToInt

/** Prices are whole VND. Null in DiscoveryFilters means no price restriction. */
data class PriceRange(val min: Int, val max: Int) {
    init { require(min >= 0 && max >= min) }
    operator fun contains(price: Int) = price in min..max
}
const val PRICE_STEP = 1_000
fun catalogPriceCeiling(catalog: List<CatalogBox>): Int {
    val highest = (catalog.maxOfOrNull { it.price } ?: 0).coerceAtLeast(100_000)
    return (((highest.toLong() + 49_999) / 50_000) * 50_000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
fun selectedPriceRange(start: Float, end: Float, ceiling: Int): PriceRange? {
    fun snap(value: Float) = ((value.coerceIn(0f, ceiling.toFloat()) / PRICE_STEP).roundToInt().toLong() * PRICE_STEP)
        .coerceIn(0, ceiling.toLong()).toInt()
    val first = snap(start)
    val second = snap(end)
    val low = minOf(first, second)
    val high = maxOf(first, second)
    return if (low == 0 && high == ceiling) null else PriceRange(low, high)
}
enum class PickupBand(val label: String) { ALL("Mọi khung giờ"), BEFORE_18("Bắt đầu trước 18:00"), FROM_18("Bắt đầu từ 18:00") }
enum class CatalogSort(val label: String) { NEAREST("Gần nhất"), PRICE("Giá thấp nhất") }
data class DiscoveryFilters(val category: BoxCategory? = null, val price: PriceRange? = null,
    val pickup: PickupBand = PickupBand.ALL, val sort: CatalogSort = CatalogSort.NEAREST) {
    val activeCount: Int get() = (if (category != null) 1 else 0) + (if (price != null) 1 else 0) + (if (pickup != PickupBand.ALL) 1 else 0) + (if (sort != CatalogSort.NEAREST) 1 else 0)
}
fun filterCatalog(query: String, district: String, filters: DiscoveryFilters, catalog: List<CatalogBox>): List<CatalogBox> {
    val result = catalog.filter {
        (district == "TP. Hồ Chí Minh" || it.district == district) &&
        (filters.category == null || it.category == filters.category) &&
        (it.name + " " + it.store + " " + it.category.label).searchKey().contains(query.trim().searchKey()) &&
        (filters.price == null || it.price in filters.price) && when (filters.pickup) {
            PickupBand.ALL -> true
            PickupBand.BEFORE_18 -> it.pickupStartHour < 18
            PickupBand.FROM_18 -> it.pickupStartHour >= 18
        }
    }
    return if (filters.sort == CatalogSort.PRICE) result.sortedBy { it.price } else result.sortedBy { if (it.distanceMeters < 0) Int.MAX_VALUE else it.distanceMeters }
}
