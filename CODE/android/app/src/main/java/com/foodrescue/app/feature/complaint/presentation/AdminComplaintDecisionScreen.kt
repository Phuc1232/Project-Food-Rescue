package com.foodrescue.app.feature.complaint.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.core.util.money
import com.foodrescue.app.feature.complaint.domain.*
import com.foodrescue.app.feature.complaint.presentation.component.reviewLabel

@Composable
internal fun AdminComplaintDecisionScreen(state: AdminComplaintsState, onBack: () -> Unit,
    onAction: (ComplaintReviewAction) -> Unit, onNote: (String) -> Unit, onAmount: (String) -> Unit,
    onContinue: () -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val row = state.selected ?: return
    FoodRescuePage("Quyết định xử lý", onBack, modifier, footer = {
        FoodRescueButton("Xem lại quyết định", onContinue, Modifier.fillMaxWidth(), enabled = !state.busy)
    }) {
        Text(row.complaint.id, style = MaterialTheme.typography.titleLarge)
        Text(row.complaint.order.store, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FoodRescueCard {
            Text("Hướng xử lý", style = MaterialTheme.typography.titleMedium)
            listOf(ComplaintReviewAction.RESOLVED, ComplaintReviewAction.REFUND_REQUESTED, ComplaintReviewAction.REJECTED).forEach { action ->
                val enabled = !state.busy && (action != ComplaintReviewAction.REFUND_REQUESTED || row.refundableAmount > 0)
                Row(Modifier.fillMaxWidth().selectable(state.decision == action, enabled = enabled, role = Role.RadioButton,
                    onClick = { onAction(action) }).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(state.decision == action, null, enabled = enabled)
                    Spacer(Modifier.width(8.dp))
                    Text(action.reviewLabel, style = MaterialTheme.typography.bodyMedium,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f))
                }
            }
            if (row.refundableAmount == 0L) Text(refundUnavailableMessage(row), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.decision == ComplaintReviewAction.REFUND_REQUESTED) FoodRescueCard {
            Text("Đề nghị hoàn qua VNPAY", style = MaterialTheme.typography.titleMedium)
            Text("Tối đa ${money(row.refundableAmount)}", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(state.amount, onAmount, Modifier.fillMaxWidth(), enabled = !state.busy,
                label = { Text("Số tiền hoàn (đ)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                isError = state.amountError != null, supportingText = { Text(state.amountError ?: "Nhập số tiền cần hoàn") })
            TextButton({ onAmount(row.refundableAmount.toString()) }, enabled = !state.busy) { Text("Toàn bộ khoản có thể hoàn") }
            Text("Quyết định này tạo yêu cầu trong hàng chờ hoàn tiền.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(state.note, onNote, Modifier.fillMaxWidth(), enabled = !state.busy, minLines = 4, maxLines = 8,
            label = { Text("Phản hồi cho khách hàng") }, placeholder = { Text("Nêu kết quả đối chiếu và lý do quyết định") },
            isError = state.noteError != null, supportingText = { Text(state.noteError ?: "${state.note.length}/2.000") })
        if (state.error != null) Text(state.error, color = MaterialTheme.colorScheme.error)
    }
    if (state.confirming) AlertDialog(onDismissRequest = onDismiss, title = { Text("Xác nhận quyết định?") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(row.complaint.id, style = MaterialTheme.typography.labelLarge)
            Text(state.decision?.reviewLabel.orEmpty(), style = MaterialTheme.typography.titleMedium)
            if (state.decision == ComplaintReviewAction.REFUND_REQUESTED) Text("Đề nghị hoàn ${money(state.amount.toLongOrNull() ?: 0)}")
            Text(state.note, maxLines = 7, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text("Phản hồi sẽ hiển thị cho khách hàng sau khi lưu.", style = MaterialTheme.typography.bodySmall)
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }, confirmButton = { TextButton(onConfirm, enabled = !state.busy) { Text("Lưu quyết định") } },
        dismissButton = { TextButton(onDismiss, enabled = !state.busy) { Text("Xem lại") } })
}
