package com.foodrescue.app.feature.surprisebox.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.core.util.money
import com.foodrescue.app.feature.store.presentation.component.*
import com.foodrescue.app.feature.surprisebox.domain.*
import com.foodrescue.app.feature.surprisebox.presentation.component.*

@Composable
internal fun SaleBatchEditorScreen(state: OwnerBoxesUiState, onBack: () -> Unit, onEdit: (SaleBatchDraft) -> Unit,
    onSave: () -> Unit, modifier: Modifier = Modifier) {
    val draft = state.batchDraft
    val old = state.editedBatch
    val template = state.batchTemplate
    val lockTemplate = old != null && (old.control != BatchControl.DRAFT || old.committed > 0)
    val lockTimes = old != null && old.committed > 0
    FoodRescuePage(if (draft.id == null) "Tạo đợt bán" else "Chỉnh sửa đợt bán", onBack, modifier, footer = {
        FoodRescueButton(if (state.busy) "Đang lưu…" else "Lưu đợt bán", onSave, Modifier.fillMaxWidth(), enabled = !state.busy)
    }) {
        Text("Sẵn sàng lên kệ", style = MaterialTheme.typography.headlineMedium)
        StoreErrorMessage(state.error)
        BoxChoice("Mẫu hộp", draft.templateId, (state.catalog.templates.filter { it.active }.map { it.id } + listOfNotNull(template?.id)).distinct(),
            { id -> (template?.takeIf { it.id == id } ?: state.catalog.templates.firstOrNull { it.id == id })?.name ?: "Chọn mẫu hộp" },
            { onEdit(draft.copy(templateId = it)) }, enabled = !state.busy && !lockTemplate)
        if (template != null) FoodRescueCard {
            StoreValue("Giá bán / hộp", money(template.price))
            StoreValue("Giá trị tối thiểu", money(template.minimumValue))
        }
        FoodRescueTextField("Số lượng hộp", draft.quantity, { onEdit(draft.copy(quantity = it)) }, enabled = !state.busy,
            placeholder = "10", error = BoxField.QUANTITY.message.takeIf { BoxField.QUANTITY in state.fields },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        if (old != null && old.committed > 0) Text("${old.held} hộp đang giữ · ${old.sold} hộp đã bán. Giá và lịch nhận đã được chốt cho khách.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        Text("Thời gian mở bán", style = MaterialTheme.typography.titleLarge)
        BoxDateTimeField("Mở bán lúc", draft.salesStart, { onEdit(draft.copy(salesStart = it)) }, enabled = !state.busy && !lockTemplate)
        BoxDateTimeField("Đóng bán lúc", draft.salesEnd, { onEdit(draft.copy(salesEnd = it)) }, enabled = !state.busy && !lockTimes)
        Text("Khung giờ nhận hàng", style = MaterialTheme.typography.titleLarge)
        BoxDateTimeField("Bắt đầu nhận", draft.pickupStart, { onEdit(draft.copy(pickupStart = it)) }, enabled = !state.busy && !lockTimes)
        BoxDateTimeField("Kết thúc nhận", draft.pickupEnd, { onEdit(draft.copy(pickupEnd = it)) }, enabled = !state.busy && !lockTimes)
        state.fields.filter { it != BoxField.QUANTITY }.forEach { Text(it.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}
