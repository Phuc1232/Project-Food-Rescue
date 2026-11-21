package com.foodrescue.app.feature.order.presentation

import com.foodrescue.app.feature.order.presentation.component.label

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.core.util.money
import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.store.presentation.component.StoreHeroCard

@Composable
internal fun OwnerOrdersScreen(storeName: String, state: OwnerOrdersUiState, onSearch: (String) -> Unit,
    onFilter: (OwnerOrderFilter) -> Unit, onPickup: (OwnerPickupFilter) -> Unit, onOpen: (String) -> Unit,
    onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Đơn hàng", style = MaterialTheme.typography.headlineLarge)
                    Text(storeName, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onRefresh, enabled = !state.busy) { Text("Tải lại") }
            }
        }
        item {
            StoreHeroCard("ƯU TIÊN NHẬN HÀNG", "${state.count(OwnerOrderFilter.AWAITING_PICKUP)} đơn chờ nhận") {
                Text("Theo dõi giờ nhận và các đơn cần xử lý", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFCCE2D7))
            }
        }
        item {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnerOrderMetric("Quá giờ nhận", state.count(OwnerOrderFilter.OVERDUE), { onFilter(OwnerOrderFilter.OVERDUE) }, Modifier.weight(1f))
                OwnerOrderMetric("Yêu cầu hủy", state.count(OwnerOrderFilter.CANCELLATION_REQUESTED), { onFilter(OwnerOrderFilter.CANCELLATION_REQUESTED) }, Modifier.weight(1f))
            }
        }
        item { FoodRescueTextField("Tìm đơn hàng", state.query, onSearch, placeholder = "Mã đơn hoặc tên hộp") }
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(OwnerOrderFilter.entries) { filter -> FilterChip(state.filter == filter, { onFilter(filter) },
                label = { Text("${filter.label} · ${state.count(filter)}") }) }
        } }
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(OwnerPickupFilter.entries) { filter -> FilterChip(state.pickupFilter == filter, { onPickup(filter) }, label = { Text(filter.label) }) }
        } }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        (state.loadError ?: state.error)?.let { error -> item { FoodRescueCard { Text(error, color = colors.error); TextButton(onRefresh) { Text("Thử lại") } } } }
        if (!state.loading && state.loadError == null && state.error == null && state.visible.isEmpty()) item {
            FoodRescueCard {
                FoodRescueGlyph(FoodRescueIcon.RECEIPT, tint = colors.primary)
                Text(if (state.orders.isEmpty()) "Chưa có đơn hàng" else "Không có đơn phù hợp", style = MaterialTheme.typography.titleLarge)
                Text(if (state.orders.isEmpty()) "Đơn đặt hộp từ đợt bán của cửa hàng sẽ xuất hiện tại đây."
                    else "Thử thay đổi từ khóa, trạng thái hoặc ngày nhận.", color = colors.onSurfaceVariant)
                if (state.orders.isNotEmpty()) TextButton({ onSearch(""); onFilter(OwnerOrderFilter.ALL); onPickup(OwnerPickupFilter.ALL) }) { Text("Xóa bộ lọc") }
            }
        }
        items(state.visible, key = { it.id }) { order ->
            Surface(onClick = { onOpen(order.id) }, shape = RoundedCornerShape(22.dp), color = colors.surface,
                border = BorderStroke(1.dp, colors.outlineVariant), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OrderStatusBadge(order.statusAt(state.now))
                    Text(order.boxSnapshot?.name ?: "Hộp bất ngờ", style = MaterialTheme.typography.titleLarge)
                    Text(order.reference, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("${order.quantity} hộp", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Text(money(order.total), style = MaterialTheme.typography.titleMedium, color = colors.primary)
                    }
                    HorizontalDivider(color = colors.outlineVariant)
                    Text(bookingDate(order.pickup.startMillis) + " · " + bookingHours(order.pickup), style = MaterialTheme.typography.bodyMedium)
                    if (order.statusAt(state.now) == OrderStatus.WAITING_PICKUP) Text(
                        when { state.now >= order.pickup.endMillis -> "Quá giờ nhận · Cần kiểm tra"
                            state.now >= order.pickup.startMillis -> "Đang trong giờ nhận hộp"
                            else -> "Sắp đến giờ nhận" }, color = if (state.now >= order.pickup.endMillis) colors.error else colors.primary,
                        style = MaterialTheme.typography.labelLarge)
                    Text("Xem chi tiết", color = colors.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun OwnerOrderMetric(title: String, count: Int, onClick: () -> Unit, modifier: Modifier) {
    Surface(onClick, modifier.fillMaxHeight(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(count.toString(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.labelLarge)
        }
    }
}
