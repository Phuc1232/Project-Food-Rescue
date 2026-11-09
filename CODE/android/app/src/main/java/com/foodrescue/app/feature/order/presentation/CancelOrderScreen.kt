package com.foodrescue.app.feature.order.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.core.util.money
import com.foodrescue.app.feature.order.domain.*

internal val CancellationReason.label: String get() = when (this) {
    CancellationReason.CHANGED_PLANS -> "Thay đổi kế hoạch"
    CancellationReason.WRONG_ORDER -> "Đặt nhầm hộp hoặc số lượng"
    CancellationReason.CANNOT_PICKUP -> "Không thể đến nhận đúng giờ"
    CancellationReason.OTHER -> "Lý do khác"
}

private val CancellationIssue.message: String get() = when (this) {
    CancellationIssue.ORDER_UNAVAILABLE -> "Không tìm thấy đơn hàng."
    CancellationIssue.NOT_PAID -> "Đơn này chưa thanh toán. Bạn có thể thôi giữ hộp trong chi tiết đơn."
    CancellationIssue.ALREADY_CANCELLED -> "Đơn này đã được hủy."
    CancellationIssue.COLLECTED -> "Đơn đã nhận hộp không thể hủy. Bạn có thể gửi khiếu nại nếu cần hỗ trợ."
    CancellationIssue.PICKUP_STARTED -> "Đã đến giờ nhận hộp. Yêu cầu hủy và hoàn tiền cần được xem xét qua khiếu nại."
    CancellationIssue.REASON_REQUIRED -> "Vui lòng chọn lý do hủy đơn."
    CancellationIssue.NOTE_TOO_LONG -> "Ghi chú tối đa 500 ký tự."
}

@Composable
internal fun CancelOrderScreen(
    state: BookingUiState,
    orderId: String,
    onBack: () -> Unit,
    onReason: (CancellationReason) -> Unit,
    onNote: (String) -> Unit,
    onConfirm: () -> Unit,
    onSupport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val order = state.reservations.firstOrNull { it.id == orderId }
    if (order == null) {
        FoodRescuePage("Hủy đơn hàng", onBack, modifier) {
            FoodRescueEmptyState("Không tìm thấy đơn hàng", "Hãy kiểm tra lại danh sách đơn của bạn.", "Quay lại", onBack)
        }
        return
    }
    val quote = cancellationQuote(order, state.nowMillis)
    val draft = state.cancellationDraft?.takeIf { it.orderId == orderId }
    val box = state.catalog.firstOrNull { it.id == order.boxId }
    var confirmOpen by rememberSaveable(order.id) { mutableStateOf(false) }
    FoodRescuePage("Hủy đơn hàng", onBack, modifier, footer = {
        FoodRescueButton(if (quote.canCancel) "Xác nhận hủy đơn" else "Gửi khiếu nại",
            if (quote.canCancel) { { confirmOpen = true } } else onSupport, Modifier.fillMaxWidth(),
            enabled = !quote.canCancel || draft?.reason != null)
    }) {
        FoodRescueCard {
            Text(box?.store ?: "Đơn hàng của bạn", style = MaterialTheme.typography.titleMedium)
            Text(order.reference, style = MaterialTheme.typography.labelLarge)
            Text("${order.quantity} hộp · " + money(order.total))
            Text("Giờ nhận: " + bookingTimestamp(order.pickup.startMillis), style = MaterialTheme.typography.bodyMedium)
        }
        FoodRescueCard {
            Text("Điều kiện hủy", style = MaterialTheme.typography.titleMedium)
            Text("Hủy trước " + bookingTimestamp(quote.deadlineMillis) + " được hoàn toàn bộ khoản đã thanh toán.",
                style = MaterialTheme.typography.bodyMedium)
            Text("Từ giờ nhận trở đi hoặc khi đã nhận hộp, yêu cầu cần được xem xét.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            quote.issue?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
        }
        if (quote.canCancel) {
            FoodRescueCard {
                Text("Khoản hoàn dự kiến", style = MaterialTheme.typography.titleMedium)
                Text(money(quote.amount), style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text("Đã thanh toán: " + money(order.total), style = MaterialTheme.typography.bodyMedium)
                Text("Phí hủy: 0 đ", style = MaterialTheme.typography.bodyMedium)
                Text("Hoàn về phương thức thanh toán ban đầu qua VNPAY.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FoodRescueCard {
                Text("Lý do hủy", style = MaterialTheme.typography.titleMedium)
                CancellationReason.entries.forEach { reason ->
                    Row(Modifier.fillMaxWidth().selectable(draft?.reason == reason, role = Role.RadioButton, onClick = { onReason(reason) })
                        .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(draft?.reason == reason, onClick = null)
                        Text(reason.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            OutlinedTextField(draft?.note.orEmpty(), onNote, modifier = Modifier.fillMaxWidth(), minLines = 2,
                label = { Text("Ghi chú (không bắt buộc)") },
                supportingText = { Text("${draft?.note?.length ?: 0}/500 ký tự") }, shape = RoundedCornerShape(14.dp))
        }
        state.cancellationIssue?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
    }
    if (confirmOpen && quote.canCancel && draft?.reason != null) AlertDialog(
        onDismissRequest = { confirmOpen = false }, title = { Text("Hủy đơn này?") },
        text = { Text("Bạn sẽ không thể dùng mã nhận hàng của đơn này. Khoản hoàn dự kiến là ${money(quote.amount)}.") },
        confirmButton = { TextButton(onClick = { confirmOpen = false; onConfirm() }) { Text("Hủy đơn") } },
        dismissButton = { TextButton(onClick = { confirmOpen = false }) { Text("Giữ đơn hàng") } },
    )
}
