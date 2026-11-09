package com.foodrescue.app.feature.order.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.feature.order.domain.*

internal val OrderStatus.label: String get() = when (this) {
    OrderStatus.WAITING_PAYMENT -> "Chờ thanh toán"
    OrderStatus.CHECKING_PAYMENT -> "Đang xác nhận thanh toán"
    OrderStatus.WAITING_PICKUP -> "Chờ nhận hộp"
    OrderStatus.COLLECTED -> "Đã nhận hộp"
    OrderStatus.CANCELLED -> "Đã hủy"
    OrderStatus.EXPIRED -> "Hết hạn thanh toán"
}

internal val OrderFilter.label: String get() = when (this) {
    OrderFilter.ALL -> "Tất cả"
    OrderFilter.UNPAID -> "Chờ thanh toán"
    OrderFilter.READY_FOR_PICKUP -> "Chờ nhận"
    OrderFilter.COLLECTED -> "Đã nhận"
    OrderFilter.CANCELLED -> "Đã hủy"
    OrderFilter.EXPIRED -> "Hết hạn"
}

@Composable
internal fun OrderStatusBadge(status: OrderStatus, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val terminal = status == OrderStatus.CANCELLED || status == OrderStatus.EXPIRED
    Surface(modifier, shape = RoundedCornerShape(10.dp),
        color = if (terminal) colors.surfaceVariant else colors.primaryContainer) {
        Text(status.label, Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (terminal) colors.onSurfaceVariant else colors.primary)
    }
}

@Composable
internal fun OrderTimeline(order: BoxReservation, nowMillis: Long, modifier: Modifier = Modifier) {
    val status = order.statusAt(nowMillis)
    FoodRescueCard(modifier) {
        Text("Tiến trình đơn hàng", style = MaterialTheme.typography.titleMedium)
        OrderStep("Đã đặt hộp", bookingTimestamp(order.createdAtMillis), true)
        if (status == OrderStatus.CANCELLED || status == OrderStatus.EXPIRED) {
            OrderStep(status.label, bookingTimestamp(order.cancelledAtMillis ?: order.expiresAtMillis), true)
        } else {
            OrderStep(if (order.isPaid) "Đã thanh toán" else "Thanh toán toàn bộ",
                if (order.isPaid) bookingTimestamp(requireNotNull(order.payment).updatedAtMillis) else "QR VNPAY", order.isPaid)
            OrderStep(if (order.collectedAtMillis != null) "Đã nhận hộp" else "Nhận tại cửa hàng",
                order.collectedAtMillis?.let(::bookingTimestamp) ?: bookingHours(order.pickup), order.collectedAtMillis != null)
        }
    }
}

@Composable
private fun OrderStep(title: String, detail: String, completed: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Surface(shape = CircleShape, color = if (completed) colors.primaryContainer else colors.surfaceVariant) {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                FoodRescueGlyph(if (completed) FoodRescueIcon.CHECK else FoodRescueIcon.CLOCK,
                    tint = if (completed) colors.primary else colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}
