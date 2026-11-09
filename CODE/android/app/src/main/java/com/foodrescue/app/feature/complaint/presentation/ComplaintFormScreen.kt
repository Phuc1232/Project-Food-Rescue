package com.foodrescue.app.feature.complaint.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.core.util.money
import com.foodrescue.app.feature.complaint.domain.*
import com.foodrescue.app.feature.complaint.presentation.component.ComplaintEvidenceCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ComplaintFormScreen(
    state: ComplaintUiState,
    orders: List<ComplaintOrder>,
    onBack: () -> Unit,
    onOrders: () -> Unit,
    onOrder: (String) -> Unit,
    onReason: (ComplaintReason) -> Unit,
    onDescription: (String) -> Unit,
    onAddEvidence: () -> Unit,
    onRemoveEvidence: (String) -> Unit,
    onSubmit: () -> Unit,
    onCase: (String) -> Unit,
    onConfirmOrderChange: () -> Unit,
    onDismissOrderChange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickOrder by rememberSaveable { mutableStateOf(false) }
    var confirm by rememberSaveable(state.draft.id) { mutableStateOf(false) }
    val draft = state.draft
    val order = orders.firstOrNull { it.id == draft.orderId }
    val existing = state.cases.firstOrNull { it.order.id == draft.orderId && it.status.isOpen }
    FoodRescuePage("Gửi khiếu nại", onBack, modifier, footer = {
        if (orders.isNotEmpty()) FoodRescueButton(if (existing != null) "Xem khiếu nại đang xử lý" else "Gửi khiếu nại", {
            if (existing != null) onCase(existing.id)
            else if (complaintIssue(draft, orders, state.cases) != null) onSubmit()
            else confirm = true
        }, Modifier.fillMaxWidth(), enabled = !state.importing)
    }) {
        if (orders.isEmpty()) {
            FoodRescueEmptyState("Chưa có đơn để khiếu nại", "Các đơn đã thực hiện thanh toán sẽ xuất hiện tại đây.", "Xem đơn hàng", onOrders)
        } else {
            FoodRescueCard {
                Text("Đơn hàng cần hỗ trợ", style = MaterialTheme.typography.titleMedium)
                if (order != null) {
                    Text(order.store, style = MaterialTheme.typography.labelLarge)
                    Text(order.reference, style = MaterialTheme.typography.bodyMedium)
                    Text("${order.quantity} hộp · ${money(order.amount)}", style = MaterialTheme.typography.bodyMedium)
                    Text(complaintTimestamp(order.createdAtMillis), style = MaterialTheme.typography.bodySmall)
                } else Text("Chọn một đơn hàng để tiếp tục.", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { pickOrder = true }, enabled = !state.importing, modifier = Modifier.fillMaxWidth()) {
                    Text(if (order == null) "Chọn đơn hàng" else "Đổi đơn hàng")
                }
            }
            if (existing != null) {
                FoodRescueCard {
                    Text("Đơn này đang được hỗ trợ", style = MaterialTheme.typography.titleMedium)
                    Text("Khiếu nại ${existing.id} · ${existing.status.label}", style = MaterialTheme.typography.bodyMedium)
                    Text("Bạn có thể theo dõi tiến trình trong chi tiết khiếu nại.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                FoodRescueCard {
                    Text("Lý do khiếu nại", style = MaterialTheme.typography.titleMedium)
                    ComplaintReason.entries.forEach { reason ->
                        Row(Modifier.fillMaxWidth().selectable(draft.reason == reason, role = Role.RadioButton,
                            onClick = { onReason(reason) }).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(draft.reason == reason, onClick = null)
                            Text(reason.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                OutlinedTextField(draft.description, onDescription, Modifier.fillMaxWidth(), minLines = 4, maxLines = 8,
                    label = { Text("Nội dung khiếu nại") }, shape = RoundedCornerShape(14.dp),
                    supportingText = { Text("${draft.description.length}/2.000 ký tự · Tối thiểu 20 ký tự") },
                    isError = state.issue in setOf(ComplaintIssue.DESCRIPTION_TOO_SHORT, ComplaintIssue.DESCRIPTION_TOO_LONG))
                Text("Ảnh bằng chứng (${draft.evidence.size}/3)", style = MaterialTheme.typography.titleMedium)
                Text("Không bắt buộc · Tối đa 5 MB mỗi ảnh", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                draft.evidence.forEach { item -> key(item.id) {
                    ComplaintEvidenceCard(item, onRemove = if (state.importing) null else { { onRemoveEvidence(item.id) } })
                } }
                if (state.importing) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text("Đang thêm ảnh…", style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedButton(onClick = onAddEvidence, enabled = !state.importing && draft.evidence.size < MAX_COMPLAINT_IMAGES,
                    modifier = Modifier.fillMaxWidth()) { Text("Thêm ảnh bằng chứng") }
                state.evidenceIssue?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
            }
            state.issue?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
        }
    }
    if (pickOrder) ModalBottomSheet(onDismissRequest = { pickOrder = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text("Chọn đơn hàng", Modifier.padding(horizontal = 24.dp, vertical = 12.dp), style = MaterialTheme.typography.titleLarge)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 500.dp), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(orders, key = { it.id }) { item ->
                OutlinedButton(onClick = { pickOrder = false; onOrder(item.id) }, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(item.store, style = MaterialTheme.typography.titleSmall)
                        Text(item.reference, style = MaterialTheme.typography.bodyMedium)
                        Text("${item.quantity} hộp · ${money(item.amount)}", style = MaterialTheme.typography.bodySmall)
                        Text(complaintTimestamp(item.createdAtMillis), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    if (state.pendingOrderId != null) AlertDialog(onDismissRequest = onDismissOrderChange,
        title = { Text("Đổi đơn hàng?") }, text = { Text("Nội dung và ảnh đang soạn sẽ được xóa để tạo khiếu nại cho đơn khác.") },
        confirmButton = { TextButton(onClick = onConfirmOrderChange) { Text("Đổi đơn") } },
        dismissButton = { TextButton(onClick = onDismissOrderChange) { Text("Giữ nội dung") } })
    if (confirm && !state.importing && existing == null) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text("Gửi khiếu nại này?") },
        text = { Text("Khiếu nại cho đơn ${order?.reference.orEmpty()} sẽ được gửi để xem xét. Bạn có thể theo dõi tiến trình trong Khiếu nại của tôi.") },
        confirmButton = { TextButton(onClick = { confirm = false; onSubmit() }) { Text("Xác nhận gửi") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Kiểm tra lại") } })
}
