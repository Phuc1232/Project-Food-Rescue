package com.foodrescue.app.feature.discovery.presentation

import com.foodrescue.app.core.common.GeoPoint
import com.foodrescue.app.feature.surprisebox.domain.BoxCategory
import com.foodrescue.app.feature.discovery.domain.*

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.foodrescue.app.feature.store.domain.PublicStoreRepository
import kotlinx.coroutines.CancellationException

/** Catalog state survives activity recreation; credentials never enter saved state. */
internal class DiscoveryViewModel(private val saved: SavedStateHandle, private val repository: CatalogRepository,
    private val publicStores: PublicStoreRepository? = null) : ViewModel() {
    private var initialError: String? = null
    var state by mutableStateOf(DiscoveryUiState(
        source = try { repository.getBoxes() } catch (_: Exception) { initialError = READ_ERROR; emptyList() },
        query = saved["query"] ?: "",
        district = saved["district"] ?: "Quận 1",
        storeResults = saved["stores"] ?: false,
        filters = DiscoveryFilters(
            category = saved.get<String>("category")?.let(BoxCategory::valueOf),
            price = saved.get<IntArray>("price")?.let { PriceRange(it[0], it[1]) },
            pickup = saved.get<String>("pickup")?.let(PickupBand::valueOf) ?: PickupBand.ALL,
            sort = saved.get<String>("sort")?.let(CatalogSort::valueOf) ?: CatalogSort.NEAREST,
        ),
        point = saved.get<DoubleArray>("point")?.let { GeoPoint(it[0], it[1], it[2].toFloat()) },
        dataError = initialError,
    ))
        private set

    private fun update(next: DiscoveryUiState) {
        state = next
        saved["query"] = next.query
        saved["district"] = next.district
        saved["stores"] = next.storeResults
        saved["category"] = next.filters.category?.name
        saved["price"] = next.filters.price?.let { intArrayOf(it.min, it.max) }
        saved["pickup"] = next.filters.pickup.name
        saved["sort"] = next.filters.sort.name
        saved["point"] = next.point?.let { doubleArrayOf(it.latitude, it.longitude, it.accuracy.toDouble()) }
    }
    fun search(value: String) = update(state.copy(query = value.take(200)))
    fun filters(value: DiscoveryFilters) = update(state.copy(filters = value))
    fun kind(stores: Boolean) = update(state.copy(storeResults = stores))
    fun district(value: String) = update(state.copy(district = value, point = null))
    fun location(value: GeoPoint) = update(state.copy(point = value, district = "TP. Hồ Chí Minh"))
    fun clearLocation() = update(state.copy(point = null))
    suspend fun refreshPublicStores() {
        try {
            val boxes = repository.getBoxes()
            val stores = publicStores?.getStores() ?: state.publicStores
            state = state.copy(source = boxes, publicStores = stores, dataError = null)
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { state = state.copy(dataError = READ_ERROR) }
    }
    fun reset() = update(state.copy(query = "", filters = DiscoveryFilters()))
    fun clear() = update(try { DiscoveryUiState(source = repository.getBoxes()) }
        catch (_: Exception) { DiscoveryUiState(source = emptyList(), dataError = READ_ERROR) })
    private companion object { const val READ_ERROR = "Chưa tải được danh sách hộp và cửa hàng. Vui lòng thử lại." }
}
