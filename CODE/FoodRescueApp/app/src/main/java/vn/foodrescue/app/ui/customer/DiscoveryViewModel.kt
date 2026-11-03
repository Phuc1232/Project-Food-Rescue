package vn.foodrescue.app.ui.customer

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DiscoveryState(val query: String = "", val district: String = "Quận 1", val filters: DiscoveryFilters = DiscoveryFilters(),
    internal val point: CustomerPoint? = null) {
    val catalog: List<DemoBox> get() = demoBoxes.map { box -> box.copy(distanceMeters = point?.let { from -> box.location?.let { distanceBetween(from, it) } } ?: -1) }
    val boxes: List<DemoBox> get() = filterCatalog(query, district, filters, catalog)
}
class DiscoveryViewModel : ViewModel() {
    private val mutable = MutableStateFlow(DiscoveryState())
    val state = mutable.asStateFlow()
    fun search(query: String) { mutable.update { it.copy(query = query) } }
    fun filters(value: DiscoveryFilters) { mutable.update { it.copy(filters = value) } }
    fun district(value: String) { mutable.update { it.copy(district = value) } }
    fun reset() { mutable.update { it.copy(query = "", filters = DiscoveryFilters()) } }
    internal fun location(point: CustomerPoint) { mutable.update { it.copy(point = point, district = "TP. Hồ Chí Minh") } }
    fun clear() { mutable.value = DiscoveryState() }
}
