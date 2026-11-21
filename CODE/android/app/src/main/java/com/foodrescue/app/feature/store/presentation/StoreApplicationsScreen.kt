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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.feature.store.domain.*
import com.foodrescue.app.feature.store.presentation.component.*

@Composable
internal fun StoreApplicationsScreen(state: StoreApplicationsState, onSearch: (String) -> Unit,
    onFilter: (ApplicationFilter) -> Unit, onOpen: (String) -> Unit, onRefresh: () -> Unit,
    modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Hồ sơ cửa hàng", style = MaterialTheme.typography.headlineMedium)
                    Text("Xét duyệt đối tác", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onRefresh, enabled = !state.busy) { Text("Làm mới") }
            }
        }
        item { FoodRescueTextField("Tìm hồ sơ", state.query, onSearch, placeholder = "Tên cửa hàng, mã hồ sơ, liên hệ",
            trailingIcon = { if (state.query.isNotBlank()) IconButton({ onSearch("") }) {
                FoodRescueGlyph(FoodRescueIcon.CLOSE, description = "Xóa tìm kiếm")
            } else FoodRescueGlyph(FoodRescueIcon.SEARCH) }) }
        item { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ApplicationFilter.entries.forEach { filter -> FilterChip(selected = state.filter == filter,
                onClick = { onFilter(filter) }, label = { Text("${filter.label} · ${filter.status?.let(state::count) ?: state.applications.size}") }) }
        } }
        if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (state.error != null) item { StoreErrorMessage(state.error) }
        if (!state.loading && state.visible.isEmpty()) item {
            StoreEmptyPanel(FoodRescueIcon.STORE, if (state.query.isBlank()) "Không có hồ sơ ${state.filter.label.lowercase()}" else "Không tìm thấy hồ sơ",
                if (state.query.isBlank()) "Hồ sơ gửi đến sẽ xuất hiện trong danh sách này." else "Thử tên cửa hàng, số điện thoại hoặc mã hồ sơ khác.")
        }
        if (state.visible.isNotEmpty()) item { Text("${state.visible.size} hồ sơ · Gửi sớm nhất trước",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(state.visible, key = { it.accountKey }) { application ->
            StoreApplicationCard(application, { onOpen(application.accountKey) }, enabled = !state.busy)
        }
    }
}

@Composable
internal fun StoreApplicationCard(application: StoreApplication, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true) {
    val store = application.store
    Surface(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(application.reference, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                StoreStatusBadge(store.status)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                StoreIconTile(FoodRescueIcon.STORE)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(store.profile.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${store.profile.category} · ${store.profile.area}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FoodRescueGlyph(FoodRescueIcon.ARROW, tint = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("Gửi lúc ${reviewDate(store.submittedAtMillis)}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
