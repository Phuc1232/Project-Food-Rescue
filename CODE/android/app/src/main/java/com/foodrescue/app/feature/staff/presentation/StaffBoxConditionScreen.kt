package com.foodrescue.app.feature.staff.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
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
import com.foodrescue.app.feature.surprisebox.domain.*
import com.foodrescue.app.feature.surprisebox.presentation.component.boxDateTime

@Composable
internal fun StaffBoxConditionScreen(state: StaffBoxConditionUiState, onBack: () -> Unit,
    onEdit: (BoxInspectionDraft) -> Unit, onConfirm: () -> Unit, onReload: () -> Unit, onReview: () -> Unit,
    modifier: Modifier = Modifier) {
    val box = state.box
    val draft = state.draft
    FoodRescuePage("Kiểm tra tình trạng hộp", onBack, modifier, footer = {
        FoodRescueButton(if (state.saved) "Xem chi tiết hộp" else if (state.busy) "Đang lưu…" else "Lưu kết quả kiểm tra",
            if (state.saved) onBack else onConfirm, Modifier.fillMaxWidth(), enabled = state.saved || state.canEdit)
    }) {
        if (state.loading) { CircularProgressIndicator(); Text("Đang tải thông tin hộp…") }
        state.error?.let { FoodRescueCard { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onReload) { Text("Tải lại") } } }
        if (box == null && !state.loading && state.error == null) {
            FoodRescueCard { Text("Không thể truy cập hộp", style = MaterialTheme.typography.titleMedium); Text("Hộp không còn khả dụng trong phạm vi công việc của bạn.") }
        }
        if (box != null) {
            Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFF133E32)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (state.saved) "ĐÃ GHI NHẬN" else "KIỂM SOÁT CHẤT LƯỢNG", style = MaterialTheme.typography.labelSmall, color = Color(0xFFD8EDB4))
                    Text(if (state.saved && box.conditionAt(state.now) == BoxCondition.REJECTED) "Đã loại khỏi bàn giao"
                        else if (state.saved) "Đã kiểm tra đạt yêu cầu" else box.templateName,
                        style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    Text(box.code, style = MaterialTheme.typography.titleSmall, color = Color(0xFFD8EDB4))
                    Text("Dùng trước ${boxDateTime(box.expiresAt)}", style = MaterialTheme.typography.bodySmall, color = Color.White)
                }
            }
            box.noShow?.let { hold -> FoodRescueCard {
                Text("Kiểm tra lại sau No-show", style = MaterialTheme.typography.titleMedium)
                Text("Đơn ${hold.reference} · ${boxDateTime(hold.atMillis)}", style = MaterialTheme.typography.bodySmall)
                Text(if (box.needsNoShowInspection()) "Hộp đang chờ kiểm tra, chưa được bàn giao." else "Đã lưu kết quả kiểm tra cho hộp của đơn này.")
            } }
            if (state.saved) BoxInspectionHistory(box)
            else {
                if (!box.canInspect()) Text("Hộp đã bàn giao hoặc bị loại. Lịch sử được giữ nguyên.", color = MaterialTheme.colorScheme.error)
                if (state.now >= box.expiresAt) Text("Hộp đã hết hạn sử dụng, không được bàn giao.", color = MaterialTheme.colorScheme.error)
                if (box.inspections.size != draft.expectedRevision || box.noShow?.requestId != draft.noShowRequestId) FoodRescueCard {
                    Text(InspectionIssue.STALE.staffMessage, color = MaterialTheme.colorScheme.error)
                    TextButton(onReview, enabled = state.canEdit) { Text("Đã xem, kiểm tra lại") }
                }
                FoodRescueCard {
                    Text("Kết quả kiểm tra", style = MaterialTheme.typography.titleMedium)
                    ConditionChoice("Đạt yêu cầu", "Giữ hộp trong danh sách đủ điều kiện giao", draft.result == InspectionResult.PASSED,
                        state.canEdit && state.now < box.expiresAt) { onEdit(draft.copy(result = InspectionResult.PASSED, reason = null)) }
                    HorizontalDivider()
                    ConditionChoice("Không đạt", "Loại hộp khỏi bàn giao và ghi nhận lý do", draft.result == InspectionResult.REJECTED,
                        state.canEdit) { onEdit(draft.copy(result = InspectionResult.REJECTED, reason = null)) }
                }
                if (draft.result == InspectionResult.PASSED) FoodRescueCard {
                    Text("Kiểm tra trực tiếp", style = MaterialTheme.typography.titleMedium)
                    ConditionCheck("Chất lượng món ăn", draft.foodChecked, state.canEdit) { onEdit(draft.copy(foodChecked = it)) }
                    ConditionCheck("Bao bì nguyên vẹn", draft.packagingChecked, state.canEdit) { onEdit(draft.copy(packagingChecked = it)) }
                    ConditionCheck("Điều kiện bảo quản", draft.storageChecked, state.canEdit) { onEdit(draft.copy(storageChecked = it)) }
                    ConditionCheck("Thông tin dị ứng", draft.allergensChecked, state.canEdit) { onEdit(draft.copy(allergensChecked = it)) }
                }
                if (draft.result == InspectionResult.REJECTED) FoodRescueCard {
                    Text("Lý do loại hộp", style = MaterialTheme.typography.titleMedium)
                    InspectionReason.entries.filter { it != InspectionReason.EXPIRED || state.now >= box.expiresAt }.forEach { reason ->
                        ConditionChoice(reason.staffLabel, null, draft.reason == reason, state.canEdit) { onEdit(draft.copy(reason = reason)) }
                    }
                }
                OutlinedTextField(draft.note, { onEdit(draft.copy(note = it)) },
                    Modifier.fillMaxWidth().semantics { contentDescription = "Ghi chú kiểm tra" }, enabled = state.canEdit,
                    label = { Text("Ghi chú kiểm tra") }, minLines = 3, maxLines = 5, shape = RoundedCornerShape(16.dp),
                    supportingText = { Text("${draft.note.length}/500" + if (draft.reason == InspectionReason.OTHER) " · Tối thiểu 10 ký tự" else "") })
                state.issues.forEach { Text(it.staffMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                BoxInspectionHistory(box)
            }
        }
    }
}

@Composable
private fun ConditionChoice(title: String, description: String?, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RadioButton(selected, null, enabled = enabled)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
@Composable
private fun ConditionCheck(title: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Checkbox(checked, null, enabled = enabled); Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun BoxInspectionHistory(box: PackedBox, modifier: Modifier = Modifier) {
    FoodRescueCard(modifier) {
        Text("Lịch sử kiểm tra", style = MaterialTheme.typography.titleMedium)
        box.inspections.asReversed().forEach { event ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (event.result == InspectionResult.REJECTED) "Đã loại hộp" else "Đã xác nhận chất lượng", style = MaterialTheme.typography.titleSmall,
                    color = if (event.result == InspectionResult.REJECTED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                Text("${event.employeeName} · ${boxDateTime(event.atMillis)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                event.reason?.let { Text(it.staffLabel, style = MaterialTheme.typography.bodyMedium) }
                if (event.noShowRequestId != null) Text("Kiểm tra lại sau No-show", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                if (event.note.isNotBlank()) Text(event.note, style = MaterialTheme.typography.bodyMedium)
            }
            HorizontalDivider()
        }
        Text("Kiểm tra khi đóng gói", style = MaterialTheme.typography.titleSmall)
        Text("${box.preparedByName} · ${boxDateTime(box.packedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
