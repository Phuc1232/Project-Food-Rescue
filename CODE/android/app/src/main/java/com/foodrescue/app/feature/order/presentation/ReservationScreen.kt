package com.foodrescue.app.feature.order.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.payment.domain.PaymentStatus
import com.foodrescue.app.feature.pickup.presentation.component.PickupCodeCard
import com.foodrescue.app.feature.refund.presentation.component.RefundSummaryCard
import com.foodrescue.app.feature.surprisebox.domain.CatalogBox

@Composable
internal fun ReservationScreen(
    reservation: BoxReservation?,
    box: CatalogBox?,
    nowMillis: Long,
    onBack: () -> Unit,
    onExplore: () -> Unit,
    onRelease: () -> Unit,
    onRebook: () -> Unit,
    onPayment: () -> Unit,
    modifier: Modifier = Modifier,
    onCancel: () -> Unit = {},
    onRefund: () -> Unit = {},
    onComplaint: () -> Unit = {},
) {
    if (reservation == null) {
        FoodRescuePage("Chi tiết đơn hàng", onBack, modifier) {
            FoodRescueEmptyState("Không tìm thấy đơn hàng", "Bạn có thể xem lại danh sách đơn của mình.", "Quay lại", onBack)
        }
        return
    }
    val status = reservation.statusAt(nowMillis)
    val active = reservation.isActive(nowMillis)
    val checking = status == OrderStatus.CHECKING_PAYMENT
    val seconds = reservation.remainingSeconds(nowMillis)
    val code = reservation.pickupCode(nowMillis)
    var releaseOpen by rememberSaveable(reservation.id) { mutableStateOf(false) }
    FoodRescuePage("Chi tiết đơn hàng", onBack, modifier, footer = {
        val label = when {
            reservation.refund != null -> "Xem hoàn tiền"
            checking || reservation.isPaid -> "Xem thanh toán"
            active -> "Thanh toán bằng QR VNPAY"
            box != null -> "Đặt lại hộp này"
            else -> "Khám phá hộp"
        }
        FoodRescueButton(label, when {
            reservation.refund != null -> onRefund
            reservation.isPaid || active -> onPayment
            box != null -> onRebook
            else -> onExplore
        }, Modifier.fillMaxWidth())
    }) {
        OrderStatusBadge(status)
        if (code != null) PickupCodeCard(code)
        reservation.refund?.let { RefundSummaryCard(it, onRefund) }
        if (status == OrderStatus.WAITING_PICKUP && nowMillis >= reservation.pickup.endMillis) {
            FoodRescueCard {
                Text("Đã qua giờ nhận hộp", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                Text("Đơn đã thanh toán. Vui lòng liên hệ cửa hàng để kiểm tra việc nhận hộp.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (active) FoodRescueCard {
            Text(if (checking) "Đang xác nhận thanh toán" else "Thời gian giữ hộp còn lại", style = MaterialTheme.typography.titleSmall)
            Text(countdown(seconds), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            LinearProgressIndicator(progress = {
                (seconds * 1000f / (reservation.expiresAtMillis - reservation.createdAtMillis)).coerceIn(0f, 1f)
            }, modifier = Modifier.fillMaxWidth())
            Text("Lượt giữ kết thúc lúc " + bookingClock(reservation.expiresAtMillis),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (box != null) BookingBoxSummary(box.copy(price = reservation.unitPrice))
        FoodRescueCard {
            Text("Thông tin đơn hàng", style = MaterialTheme.typography.titleMedium)
            Text(reservation.reference, style = MaterialTheme.typography.labelLarge)
            Text("Đặt lúc " + bookingTimestamp(reservation.createdAtMillis), style = MaterialTheme.typography.bodySmall)
            if (box != null) Text(box.name, style = MaterialTheme.typography.bodyMedium)
            Text("Số lượng: " + reservation.quantity + " hộp", style = MaterialTheme.typography.bodyLarge)
            BookingAmount("Tổng tiền", reservation.total)
        }
        if (box != null) {
            BookingPickupCard(box, reservation.pickup)
            if (status == OrderStatus.WAITING_PICKUP) PickupDirectionsButton(box.location)
        } else FoodRescueCard {
            Text("Giờ nhận tại cửa hàng", style = MaterialTheme.typography.titleMedium)
            Text(bookingDate(reservation.pickup.startMillis) + "\n" + bookingHours(reservation.pickup))
            Text("Thông tin cửa hàng hiện không khả dụng.", style = MaterialTheme.typography.bodySmall)
        }
        FoodRescueCard {
            Text("Thông tin thanh toán", style = MaterialTheme.typography.titleMedium)
            Text("QR VNPAY · Thanh toán toàn bộ", style = MaterialTheme.typography.bodyMedium)
            val payment = reservation.payment
            Text(when {
                reservation.isPaid -> "Đã thanh toán toàn bộ"
                status == OrderStatus.CANCELLED -> "Đã hủy lượt giữ · Chưa thanh toán"
                status == OrderStatus.EXPIRED -> "Đã hết hạn thanh toán"
                checking -> "Đang xác nhận thanh toán"
                payment?.status == PaymentStatus.FAILED -> "Chưa thể xác nhận thanh toán"
                else -> "Chưa thanh toán"
            }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            if (payment != null) {
                Text("Mã thanh toán: " + payment.reference, style = MaterialTheme.typography.bodySmall)
                if (reservation.isPaid) {
                    BookingAmount("Đã thanh toán", payment.amount)
                    Text(bookingTimestamp(payment.updatedAtMillis), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        OrderTimeline(reservation, nowMillis)
        if (reservation.isPaid && reservation.cancelledAtMillis == null && reservation.collectedAtMillis == null) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Hủy đơn và hoàn tiền") }
        }
        if (reservation.payment != null) {
            TextButton(onClick = onComplaint, modifier = Modifier.fillMaxWidth()) { Text("Gửi khiếu nại") }
        }
        if (active && !checking) TextButton(onClick = { releaseOpen = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Thôi giữ hộp", color = MaterialTheme.colorScheme.error)
        }
        TextButton(onClick = onExplore, modifier = Modifier.fillMaxWidth()) { Text("Tiếp tục khám phá") }
    }
    if (releaseOpen && active && !checking) AlertDialog(onDismissRequest = { releaseOpen = false },
        title = { Text("Thôi giữ hộp?") },
        text = { Text("Số hộp này sẽ được trả lại danh sách. Chưa phát sinh thanh toán.") },
        confirmButton = { TextButton(onClick = { releaseOpen = false; onRelease() }) { Text("Thôi giữ hộp") } },
        dismissButton = { TextButton(onClick = { releaseOpen = false }) { Text("Tiếp tục giữ") } })
}
