package com.foodrescue.app.feature.staff.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.core.util.money
import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.order.presentation.component.HandoverHistory
import com.foodrescue.app.feature.surprisebox.domain.*
import com.foodrescue.app.feature.surprisebox.presentation.component.boxDateTime

@Composable
internal fun StaffNoShowScreen(state: StaffNoShowUiState, onBack: () -> Unit, onEdit: (NoShowDraft) -> Unit,
    onConfirm: () -> Unit, onReload: () -> Unit, onInspect: (PackedBox) -> Unit, modifier: Modifier = Modifier) {
    val order = state.order
    val receipt = order?.noShow
    val blocking = state.validation - setOf(NoShowIssue.CHECKS, NoShowIssue.NOTE, NoShowIssue.BOXES)
    FoodRescuePage(if (receipt == null) "Xác minh quá giờ nhận" else "Kết quả xác minh", onBack, modifier, footer = {
        FoodRescueButton(if (receipt != null) "Về danh sách đơn" else if (state.busy) "Đang ghi nhận…" else "Xác nhận không đến nhận",
            if (receipt != null) onBack else onConfirm, Modifier.fillMaxWidth(), enabled = receipt != null || (state.canEdit && blocking.isEmpty()))
    }) {
        if (state.loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Đang đối chiếu đơn hàng…") }
        state.error?.let { FoodRescueCard { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onReload) { Text("Tải lại dữ liệu") } } }
        if (!state.loading && order == null) Text("Không thể truy cập đơn trong phạm vi công việc của bạn.")
        if (order != null) {
            Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFF133E32)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (receipt == null) "ĐỐI CHIẾU TẠI CỬA HÀNG" else "ĐÃ GHI NHẬN", style = MaterialTheme.typography.labelSmall, color = Color(0xFFD8EDB4))
                    Text(if (receipt == null) "Đơn đã qua giờ nhận" else "Đã xác nhận không đến nhận",
                        style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    Text(order.reference, style = MaterialTheme.typography.titleSmall, color = Color(0xFFD8EDB4))
                    Text("Kết thúc nhận: ${boxDateTime(order.pickup.endMillis)}", style = MaterialTheme.typography.bodySmall, color = Color.White)
                    if (order.noShowEligibleAt > order.pickup.endMillis) Text("Có thể xác minh từ ${boxDateTime(order.noShowEligibleAt)}",
                        style = MaterialTheme.typography.bodySmall, color = Color.White)
                }
            }
            FoodRescueCard {
                Text(order.boxSnapshot?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
                Text("${order.quantity} hộp · Đã thanh toán ${money(order.total)}")
                Text("QR VNPAY · Thanh toán toàn bộ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (receipt != null) {
                HandoverHistory(order)
                Text("Hộp liên quan", style = MaterialTheme.typography.titleLarge)
                if (receipt.boxIds.isEmpty()) FoodRescueCard { Text("Không có hộp được gắn riêng với đơn này.") }
                receipt.boxIds.forEach { id -> state.workspace.boxes.firstOrNull { it.id == id }?.let { box ->
                    FoodRescueCard {
                        Text(box.code, style = MaterialTheme.typography.titleSmall)
                        Text(box.conditionAt(state.now).staffLabel, color = MaterialTheme.colorScheme.primary)
                        Text("Dùng trước ${boxDateTime(box.expiresAt)}", style = MaterialTheme.typography.bodySmall)
                        if (state.current && !state.busy && state.workspace.access.canInspectBoxes() && box.canInspect())
                            OutlinedButton({ onInspect(box) }, Modifier.fillMaxWidth()) { Text("Kiểm tra lại hộp") }
                    }
                } }
            } else {
                blocking.forEach { Text(it.noShowMessage, color = MaterialTheme.colorScheme.error) }
                FoodRescueCard {
                    Text("01  Xác minh thực tế", style = MaterialTheme.typography.titleMedium)
                    NoShowCheck("Đã đối chiếu: khách chưa nhận hàng", state.draft.customerAbsent, state.canEdit) {
                        onEdit(state.draft.copy(customerAbsent = it))
                    }
                    NoShowCheck("Cửa hàng có thể giao đúng cam kết", state.draft.storeReady, state.canEdit) {
                        onEdit(state.draft.copy(storeReady = it))
                    }
                    Text("Nếu cửa hàng không thể giao hoặc khách đã đến nhưng chưa nhận được hộp, ghi nhận sự cố bàn giao.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("02  Hộp đã để riêng", style = MaterialTheme.typography.titleLarge)
                Text("Chỉ chọn hộp thực tế đã chuẩn bị riêng cho đơn. Hộp được chọn sẽ chờ kiểm tra lại trước khi bàn giao.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (state.draft.boxIds.isEmpty()) "Chưa chọn hộp để riêng" else "Đã chọn ${state.draft.boxIds.size}/${order.quantity} hộp",
                    style = MaterialTheme.typography.labelLarge)
                state.availableBoxes.forEach { box ->
                    val selected = box.id in state.draft.boxIds
                    val enabled = state.canEdit && (selected || state.draft.boxIds.size < order.quantity)
                    Surface(shape = RoundedCornerShape(20.dp), color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.fillMaxWidth().toggleable(selected, enabled = enabled, role = Role.Checkbox, onValueChange = {
                            onEdit(state.draft.copy(boxIds = if (selected) state.draft.boxIds - box.id else state.draft.boxIds + box.id))
                        }).semantics { contentDescription = "Hộp để riêng ${box.code}" }.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(box.code, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                Checkbox(selected, null, enabled = enabled)
                            }
                            Text(box.templateName, style = MaterialTheme.typography.bodyMedium)
                            Text("Dùng trước ${boxDateTime(box.expiresAt)}", style = MaterialTheme.typography.bodySmall)
                            if (state.now >= box.expiresAt) Text("Đã hết hạn · cần kiểm tra loại hộp", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                OutlinedTextField(state.draft.note, { onEdit(state.draft.copy(note = it)) },
                    Modifier.fillMaxWidth().semantics { contentDescription = "Ghi chú xác minh" }, label = { Text("Ghi chú xác minh") },
                    minLines = 3, maxLines = 5, enabled = state.canEdit, shape = RoundedCornerShape(16.dp),
                    supportingText = { Text("${state.draft.note.length}/500") })
                (state.issues - blocking).forEach { Text(it.noShowMessage, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun NoShowCheck(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Checkbox(checked, null, enabled = enabled)
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

private val NoShowIssue.noShowMessage: String get() = when (this) {
    NoShowIssue.ACCESS -> "Bạn chưa có quyền xác nhận đơn này."
    NoShowIssue.ORDER -> "Đơn không còn ở trạng thái cho phép xác nhận No-show."
    NoShowIssue.PAYMENT -> "Đơn chưa được xác nhận thanh toán đầy đủ."
    NoShowIssue.EARLY -> "Chưa hết giờ nhận và thời gian chờ theo chính sách của đơn."
    NoShowIssue.INCIDENT -> "Đơn có sự cố bàn giao. Chuyển chủ cửa hàng xử lý, không xác nhận No-show."
    NoShowIssue.CHECKS -> "Đối chiếu và xác nhận đủ hai điều kiện tại cửa hàng."
    NoShowIssue.BOXES -> "Hộp đã chọn không còn phù hợp. Tải lại và chọn đúng hộp đã để riêng."
    NoShowIssue.NOTE -> "Ghi chú tối đa 500 ký tự và không có ký tự điều khiển."
    NoShowIssue.REQUEST -> "Phiên xác minh không hợp lệ. Mở lại đơn để tiếp tục."
}
