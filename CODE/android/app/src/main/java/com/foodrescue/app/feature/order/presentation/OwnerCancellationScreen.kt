package com.foodrescue.app.feature.order.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.core.util.money
import com.foodrescue.app.feature.order.domain.*

@Composable
internal fun OwnerCancellationScreen(order: BoxReservation, draft: OwnerCancellationDraft, busy: Boolean,
    error: String?, onReason: (OwnerCancellationReason) -> Unit, onNote: (String) -> Unit,
    onBack: () -> Unit, onReview: () -> Unit, modifier: Modifier = Modifier) {
    FoodRescuePage("Yêu cầu hủy đơn", onBack, modifier, footer = {
        FoodRescueButton(if (busy) "Đang gửi…" else "Xem lại yêu cầu", onReview, Modifier.fillMaxWidth(), enabled = !busy)
    }) {
        Text("Không thể cung cấp hàng", style = MaterialTheme.typography.headlineMedium)
        Text("${order.reference} · ${order.quantity} hộp", color = MaterialTheme.colorScheme.onSurfaceVariant)
        FoodRescueCard {
            Text("Thông tin xử lý", style = MaterialTheme.typography.titleMedium)
            Text("Khách đã thanh toán ${money(order.total)} qua QR VNPAY.")
            Text("Nếu yêu cầu được chấp thuận, khách được hoàn toàn bộ ${money(order.total)}.")
            Text("Yêu cầu sẽ ở trạng thái chờ xử lý. Việc gửi yêu cầu chưa đồng nghĩa đơn đã hủy hoặc tiền đã hoàn.")
        }
        Text("Lý do không thể cung cấp", style = MaterialTheme.typography.titleMedium)
        OwnerCancellationReason.entries.forEach { reason ->
            Surface(onClick = { if (!busy) onReason(reason) }, shape = MaterialTheme.shapes.medium,
                color = if (draft.reason == reason) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(draft.reason == reason, onClick = null, enabled = !busy)
                    Text(reason.label, Modifier.padding(start = 12.dp))
                }
            }
        }
        val noteLabel = if (draft.reason == OwnerCancellationReason.OTHER) "Mô tả lý do (bắt buộc)" else "Thông tin bổ sung"
        Text(noteLabel, style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(draft.note, onNote, Modifier.fillMaxWidth().semantics { contentDescription = noteLabel },
            enabled = !busy, minLines = 3, maxLines = 6, shape = RoundedCornerShape(12.dp),
            placeholder = { Text("Nội dung gửi cùng yêu cầu") })
        Text("${draft.note.length}/500 ký tự", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
