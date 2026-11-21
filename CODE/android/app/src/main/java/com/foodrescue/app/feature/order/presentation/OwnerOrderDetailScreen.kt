package com.foodrescue.app.feature.order.presentation

import com.foodrescue.app.feature.order.presentation.component.label

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.core.util.money
import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.order.presentation.component.HandoverHistory
import com.foodrescue.app.feature.pickup.presentation.component.PickupCodeCard
import com.foodrescue.app.feature.refund.presentation.component.label

@Composable
internal fun OwnerOrderDetailScreen(order: BoxReservation, now: Long, onBack: () -> Unit,
    onCancellation: () -> Unit, modifier: Modifier = Modifier) {
    val canCancel = order.noShow == null && order.isPaid && order.cancelledAtMillis == null && order.collectedAtMillis == null && order.ownerCancellation == null
    FoodRescuePage("Chi tiết đơn hàng", onBack, modifier, footer = if (canCancel) ({
        OutlinedButton(onCancellation, Modifier.fillMaxWidth()) { Text("Yêu cầu hủy đơn") }
    }) else null) {
        OrderStatusBadge(order.statusAt(now))
        Text(order.reference, style = MaterialTheme.typography.titleLarge)
        Text("Đặt lúc ${bookingTimestamp(order.createdAtMillis)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        FoodRescueCard {
            Text(order.boxSnapshot?.name ?: "Hộp bất ngờ", style = MaterialTheme.typography.headlineSmall)
            Text("${order.quantity} hộp × ${money(order.unitPrice.toLong())}")
            BookingAmount("Tổng tiền", order.total)
            order.boxSnapshot?.let { Text("Giá trị tối thiểu mỗi hộp: ${money(it.minimumValue.toLong())}") }
        }
        FoodRescueCard {
            Text("Lịch nhận tại cửa hàng", style = MaterialTheme.typography.titleMedium)
            Text(bookingDate(order.pickup.startMillis), style = MaterialTheme.typography.titleLarge)
            Text(bookingHours(order.pickup), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            if (order.statusAt(now) == OrderStatus.WAITING_PICKUP && now >= order.pickup.endMillis) Text(
                "Đã qua giờ nhận. Cần kiểm tra tình trạng đơn; đơn chưa được ghi nhận là đã giao.", color = MaterialTheme.colorScheme.error)
        }
        order.pickupCode(now)?.let { PickupCodeCard(it) }
        FoodRescueCard {
            Text("Thanh toán", style = MaterialTheme.typography.titleMedium)
            Text("QR VNPAY · Thanh toán toàn bộ")
            Text(if (order.isPaid) "Đã thanh toán ${money(order.total)}" else "Chưa xác nhận thanh toán",
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            order.payment?.let { Text("Mã giao dịch: ${it.reference}"); Text(bookingTimestamp(it.updatedAtMillis)) }
        }
        order.ownerCancellation?.let { request -> FoodRescueCard {
            Text("Yêu cầu hủy đang chờ xử lý", style = MaterialTheme.typography.titleMedium)
            Text(request.reason.label)
            if (request.note.isNotBlank()) Text(request.note)
            Text("Gửi lúc ${bookingTimestamp(request.requestedAtMillis)}", style = MaterialTheme.typography.bodySmall)
            Text("Chưa phát sinh hoàn tiền. Số hộp của đơn vẫn được ghi nhận đến khi có kết quả xử lý.")
        } }
        order.refund?.let { refund -> FoodRescueCard {
            Text("Hoàn tiền", style = MaterialTheme.typography.titleMedium)
            Text(refund.status.label); BookingAmount("Số tiền", refund.amount)
        } }
        HandoverHistory(order)
        order.adminRefunds.sortedByDescending { it.updatedAtMillis }.forEach {
            com.foodrescue.app.feature.refund.presentation.component.ManagedRefundSummary(it)
        }
        OrderTimeline(order, now)
    }
}
