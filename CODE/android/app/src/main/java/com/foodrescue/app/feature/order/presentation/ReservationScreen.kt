package com.foodrescue.app.feature.order.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.payment.domain.PaymentStatus
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
) {
    if (reservation == null || box == null) {
        FoodRescuePage("Hộp đang giữ", onBack, modifier) {
            FoodRescueEmptyState("Lượt giữ không còn khả dụng", "Bạn có thể chọn một hộp khác.", "Khám phá hộp", onExplore)
        }
        return
    }
    val active = reservation.isActive(nowMillis)
    val paid = reservation.isPaid
    val checking = reservation.payment?.status == PaymentStatus.PROCESSING
    val seconds = reservation.remainingSeconds(nowMillis)
    var releaseOpen by rememberSaveable(reservation.id) { mutableStateOf(false) }
    FoodRescuePage(if (paid) "Đơn đã xác nhận" else if (active) "Hộp đang giữ" else "Đã hết thời gian giữ hộp", onBack, modifier, footer = {
        FoodRescueButton(if (paid || checking) "Xem thanh toán" else if (active) "Thanh toán bằng QR VNPAY" else "Đặt lại hộp này",
            if (paid || active) onPayment else onRebook, Modifier.fillMaxWidth())
    }) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    FoodRescueGlyph(if (active || paid) FoodRescueIcon.CHECK else FoodRescueIcon.CLOCK, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text(if (paid) "Hộp đã được xác nhận" else if (active) "Đã giữ " + reservation.quantity + " hộp cho bạn" else "Lượt giữ hộp đã kết thúc",
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(if (paid) "Đã thanh toán toàn bộ · Chờ nhận hộp" else if (checking) "Đang xác nhận thanh toán" else if (active) "Chờ thanh toán" else "Thời gian thanh toán đã kết thúc.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        if (!paid) FoodRescueCard {
            Text(if (active) "Thời gian giữ hộp còn lại" else "Thời gian giữ hộp", style = MaterialTheme.typography.titleSmall)
            Text(countdown(seconds), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            LinearProgressIndicator(progress = {
                (seconds * 1000f / (reservation.expiresAtMillis - reservation.createdAtMillis)).coerceIn(0f, 1f)
            }, modifier = Modifier.fillMaxWidth())
            Text(if (active) "Lượt giữ kết thúc lúc " + bookingClock(reservation.expiresAtMillis)
                else "Hộp đã được trả lại danh sách. Kiểm tra số lượng còn lại khi đặt lại.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BookingBoxSummary(box.copy(price = reservation.unitPrice))
        FoodRescueCard {
            Text("Số lượng: " + reservation.quantity + " hộp", style = MaterialTheme.typography.bodyLarge)
            BookingAmount(if (paid) "Đã thanh toán" else "Tổng tiền", reservation.total)
            Text("QR VNPAY · Thanh toán toàn bộ", style = MaterialTheme.typography.bodyMedium)
        }
        BookingPickupCard(box, reservation.pickup)
        if (active && !checking) TextButton(onClick = { releaseOpen = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Thôi giữ hộp", color = MaterialTheme.colorScheme.error)
        }
        TextButton(onClick = onExplore, modifier = Modifier.fillMaxWidth()) { Text("Tiếp tục khám phá") }
    }
    if (releaseOpen) AlertDialog(onDismissRequest = { releaseOpen = false },
        title = { Text("Thôi giữ hộp?") },
        text = { Text("Số hộp này sẽ được trả lại danh sách. Chưa phát sinh thanh toán.") },
        confirmButton = { TextButton(onClick = { releaseOpen = false; onRelease() }) { Text("Thôi giữ hộp") } },
        dismissButton = { TextButton(onClick = { releaseOpen = false }) { Text("Tiếp tục giữ") } })
}
