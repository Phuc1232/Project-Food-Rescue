package com.foodrescue.app.feature.discovery.presentation

import com.foodrescue.app.feature.surprisebox.domain.CatalogBox
import com.foodrescue.app.feature.surprisebox.domain.BoxCategory
import com.foodrescue.app.core.common.GeoPoint
import com.foodrescue.app.core.util.distanceBetween
import com.foodrescue.app.core.util.money
import com.foodrescue.app.feature.discovery.domain.*
import com.foodrescue.app.feature.store.domain.PublicStore
import com.foodrescue.app.core.util.searchKey

internal data class DiscoveryShop(val id: Int,val name: String,val area: String,val distanceMeters: Int,
    val fromPrice: Long,val count: Long,val category: BoxCategory = BoxCategory.MIXED)
internal data class DiscoveryListUiState(
    val query: String, val district: String, val filterCount: Int, val filterSummary: String,
    val boxes: List<CatalogBox>, val shops: List<DiscoveryShop>, val total: Long,
    val storeResults: Boolean = false,
)
internal data class DiscoveryMapUiState(
    val boxes: List<CatalogBox>, val district: String, val query: String,
    val filterCount: Int, val point: GeoPoint?, val totalResults: Int,
    val publicStores: List<PublicStore> = emptyList(),
)

internal data class DiscoveryUiState(
    val query: String = "",
    val district: String = "Quận 1",
    val filters: DiscoveryFilters = DiscoveryFilters(),
    val point: GeoPoint? = null,
    val storeResults: Boolean = false,
    val source: List<CatalogBox>,
    val publicStores: List<PublicStore> = emptyList(),
    val dataError: String? = null,
) {
    val catalog: List<CatalogBox> get() = source.map { box ->
        box.copy(distanceMeters = point?.let { from -> box.location?.let { distanceBetween(from, it) } } ?: -1)
    }
    val boxes: List<CatalogBox> get() = filterCatalog(query, district, filters, catalog)
    val visiblePublicStores: List<PublicStore> get() {
        if (filters.category != null || filters.price != null || filters.pickup != PickupBand.ALL) return emptyList()
        val key = query.searchKey()
        val representedStores = boxes.map { it.storeId }.toSet()
        return publicStores.filter {
            it.id !in representedStores && (district == "TP. Hồ Chí Minh" || it.area == district) &&
                (key.isBlank() || (it.name + " " + it.category + " " + it.location.address).searchKey().contains(key))
        }.sortedBy { store -> point?.let { distanceBetween(it, store.location) } ?: Int.MAX_VALUE }
    }
    val shops: List<DiscoveryShop> get() = boxes.groupBy { it.storeId }.map { (id, offers) ->
        val first = offers.first()
        DiscoveryShop(id, first.store, first.district, first.distanceMeters,
            offers.minOf { it.price }.toLong(), offers.size.toLong(), first.category)
    } + visiblePublicStores.map { store -> DiscoveryShop(store.id, store.name, store.area,
        point?.let { distanceBetween(it, store.location) } ?: -1, 0, 0) }
    val presentation: DiscoveryListUiState get() = DiscoveryListUiState(
        query, district, filters.activeCount,
        listOfNotNull(filters.category?.label, filters.price?.let { "${money(it.min)} – ${money(it.max)}" },
            filters.pickup.takeIf { it != PickupBand.ALL }?.label,
            filters.sort.takeIf { it != CatalogSort.NEAREST }?.label).joinToString(" · "),
        boxes, shops,
        if (storeResults) shops.size.toLong() else boxes.size.toLong(),
        storeResults = storeResults,
    )
    val mapPresentation: DiscoveryMapUiState get() {
        val pins = boxes.filter { it.location != null }.groupBy { it.storeId }
            .values.map { offers -> offers.minBy { it.price } }
        return DiscoveryMapUiState(pins, district, query, filters.activeCount, point, boxes.size, visiblePublicStores)
    }
}
