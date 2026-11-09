package com.foodrescue.app.feature.order.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.FoodRescueEmptyState
import com.foodrescue.app.core.util.money
import com.foodrescue.app.feature.order.domain.*

@Composable
internal fun CustomerOrdersScreen(
    state: BookingUiState,
    onReservation: (String) -> Unit,
    onExplore: () -> Unit,
    onFilter: (OrderFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(top = 24.dp)) {
        Text("Đơn hàng", Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        LazyRow(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(OrderFilter.entries, key = { it.name }) { filter ->
                FilterChip(selected = filter == state.orderFilter, onClick = { onFilter(filter) }, label = {
                    Text(filter.label + " (" + filter.select(state.reservations, state.nowMillis).size + ")")
                })
            }
        }
        if (state.visibleOrders.isEmpty()) {
            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(24.dp)) {
                item {
                    val emptyHistory = state.reservations.isEmpty()
                    FoodRescueEmptyState(
                        if (emptyHistory) "Bạn chưa có đơn hàng" else "Chưa có đơn trong mục này",
                        if (emptyHistory) "Khám phá những hộp bất ngờ từ các cửa hàng quanh bạn."
                        else "Các đơn ${state.orderFilter.label.lowercase()} sẽ xuất hiện tại đây.",
                        if (emptyHistory) "Khám phá hộp" else "Xem tất cả đơn",
                        if (emptyHistory) onExplore else { { onFilter(OrderFilter.ALL) } },
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(state.visibleOrders, key = { it.id }) { reservation ->
                    val box = state.catalog.firstOrNull { it.id == reservation.boxId }
                    val status = reservation.statusAt(state.nowMillis)
                    Surface(onClick = { onReservation(reservation.id) }, shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OrderStatusBadge(status)
                            Text(box?.store ?: "Hộp bất ngờ", style = MaterialTheme.typography.titleLarge)
                            Text(reservation.reference, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(reservation.quantity.toString() + " hộp · " + money(reservation.total), fontWeight = FontWeight.SemiBold)
                            Text(bookingDate(reservation.pickup.startMillis) + "\n" + bookingHours(reservation.pickup),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (reservation.isActive(state.nowMillis)) {
                                Text("Giữ hộp còn " + countdown(reservation.remainingSeconds(state.nowMillis)),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            } else if (status == OrderStatus.WAITING_PICKUP && state.nowMillis >= reservation.pickup.endMillis) {
                                Text("Đã qua giờ nhận hộp", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                            Text("Xem chi tiết đơn", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
