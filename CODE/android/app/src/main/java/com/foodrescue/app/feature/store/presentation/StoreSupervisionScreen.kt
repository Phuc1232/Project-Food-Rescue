package com.foodrescue.app.feature.store.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.feature.store.domain.*
import com.foodrescue.app.feature.store.presentation.component.*

@Composable
internal fun StoreSupervisionScreen(state: StoreSupervisionState, onSearch: (String) -> Unit,
    onFilter: (SupervisionFilter) -> Unit, onOpen: (String) -> Unit, onRefresh: () -> Unit,
    modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("CHẤT LƯỢNG ĐỐI TÁC", style = MaterialTheme.typography.labelMedium)
                    Text("Giám sát cửa hàng", style = MaterialTheme.typography.headlineMedium)
                    Text("Đối chiếu chất lượng, theo dõi vận hành và kiểm soát quyền bán.", style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .25f))
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Column(Modifier.weight(1f)) { Text("${state.stores.size}", style = MaterialTheme.typography.headlineLarge); Text("Đã duyệt", style = MaterialTheme.typography.labelMedium) }
                        Column(Modifier.weight(1f)) { Text("${state.restrictedCount}", style = MaterialTheme.typography.headlineLarge); Text("Có hạn chế", style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
        }
        item { FoodRescueTextField("Tìm cửa hàng", state.query, onSearch, placeholder = "Tên, mã cửa hàng, khu vực",
            trailingIcon = { if (state.query.isNotBlank()) IconButton({ onSearch("") }) {
                FoodRescueGlyph(FoodRescueIcon.CLOSE, description = "Xóa tìm kiếm")
            } else FoodRescueGlyph(FoodRescueIcon.SEARCH) }) }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SupervisionFilter.entries.forEach { FilterChip(state.filter == it, { onFilter(it) }, label = { Text(it.label) }) }
        } }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${state.visible.size} cửa hàng", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            TextButton(onRefresh, enabled = !state.busy) { Text("Làm mới") }
        } }
        if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.error?.let { item { StoreErrorMessage(it) } }
        if (!state.loading && state.visible.isEmpty() && state.error == null) item {
            StoreEmptyPanel(FoodRescueIcon.STORE, "Chưa có cửa hàng phù hợp",
                if (state.stores.isEmpty()) "Cửa hàng đã được duyệt sẽ xuất hiện tại đây." else "Thử đổi từ khóa hoặc bộ lọc.")
        }
        items(state.visible, key = { it.accountKey }) { entry ->
            Surface(onClick = { onOpen(entry.accountKey) }, enabled = !state.busy, shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(entry.reference, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StoreIconTile(FoodRescueIcon.STORE)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(entry.store.profile.name, style = MaterialTheme.typography.titleMedium)
                            Text("${entry.store.profile.category} · ${entry.store.profile.area}", style = MaterialTheme.typography.bodySmall)
                        }
                        FoodRescueGlyph(FoodRescueIcon.CHEVRON)
                    }
                    StoreOperatingBadge(entry.store.governance)
                }
            }
        }
    }
}

@Composable
internal fun StoreOperatingBadge(governance: StoreGovernance, modifier: Modifier = Modifier) {
    val suspended = governance.suspension != null
    val blocked = governance.blockedBatches.size
    Surface(modifier, color = if (suspended) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(10.dp)) {
        Text(if (suspended) "Tạm ngừng bán" else if (blocked > 0) "$blocked đợt bán bị hạn chế" else "Được phép bán",
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
    }
}
