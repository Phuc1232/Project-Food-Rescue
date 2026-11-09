package com.foodrescue.app.feature.order.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.FoodRescueEmptyState
import com.foodrescue.app.core.util.money
import com.foodrescue.app.feature.payment.domain.PaymentStatus

@Composable
internal fun CustomerOrdersScreen(
    state: BookingUiState,
    onReservation: (String) -> Unit,
    onExplore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(top = 24.dp)) {
        Text("Đơn hàng", Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        if (state.reservations.isEmpty()) {
            Spacer(Modifier.weight(1f))
            FoodRescueEmptyState("Bạn chưa có đơn hàng", "Khám phá những hộp bất ngờ từ các cửa hàng quanh bạn.", "Khám phá hộp", onExplore)
            Spacer(Modifier.weight(1f))
        } else {
            LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { Text("Hộp của bạn", style = MaterialTheme.typography.titleMedium) }
                items(state.reservations, key = { it.id }) { reservation ->
                    val box = state.catalog.firstOrNull { it.id == reservation.boxId }
                    val active = reservation.isActive(state.nowMillis)
                    val paid = reservation.isPaid
                    val checking = reservation.payment?.status == PaymentStatus.PROCESSING
                    Surface(onClick = { onReservation(reservation.id) }, shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(if (paid) "Đã thanh toán · Chờ nhận hộp" else if (checking) "Đang xác nhận thanh toán"
                                else if (active) "Chờ thanh toán · " + countdown(reservation.remainingSeconds(state.nowMillis)) else "Hết thời gian giữ hộp",
                                color = if (active || paid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelLarge)
                            Text(box?.store ?: "Cửa hàng", style = MaterialTheme.typography.titleLarge)
                            Text(reservation.quantity.toString() + " hộp · " + money(reservation.total))
                            Text(bookingDate(reservation.pickup.startMillis) + "\n" + bookingHours(reservation.pickup),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (paid) "Xem đơn hàng" else "Xem lượt giữ hộp", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
