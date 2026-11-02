package vn.foodrescue.app.ui.customer

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DiscoveryState(val query: String = "") {
    val district: String get() = "TP. Hồ Chí Minh"
    val boxes: List<DemoBox> get() = searchCatalog(query)
}
class DiscoveryViewModel : ViewModel() {
    private val mutable = MutableStateFlow(DiscoveryState())
    val state = mutable.asStateFlow()
    fun search(query: String) { mutable.update { it.copy(query = query) } }
    fun reset() { mutable.update { it.copy(query = "") } }
}
