package com.foodrescue.app.feature.surprisebox.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.feature.store.presentation.component.StoreErrorMessage
import com.foodrescue.app.feature.surprisebox.domain.*
import com.foodrescue.app.feature.surprisebox.presentation.component.BoxChoice

@Composable
internal fun BoxTemplateEditorScreen(state: OwnerBoxesUiState, onBack: () -> Unit, onEdit: (BoxTemplateDraft) -> Unit,
    onSave: () -> Unit, modifier: Modifier = Modifier) {
    val draft = state.templateDraft
    fun error(field: BoxField) = field.message.takeIf { field in state.fields }
    FoodRescuePage(if (draft.id == null) "Tạo mẫu hộp" else "Chỉnh sửa mẫu hộp", onBack, modifier, footer = {
        FoodRescueButton(if (state.busy) "Đang lưu…" else "Lưu mẫu hộp", onSave, Modifier.fillMaxWidth(), enabled = !state.busy)
    }) {
        Text("Một bất ngờ thật ngon", style = MaterialTheme.typography.headlineMedium)
        StoreErrorMessage(state.error)
        FoodRescueTextField("Tên mẫu hộp", draft.name, { onEdit(draft.copy(name = it)) }, enabled = !state.busy,
            placeholder = "Ví dụ: Hộp bánh cuối ngày", error = error(BoxField.NAME) ?: error(BoxField.DUPLICATE))
        BoxChoice("Nhóm thực phẩm", draft.category, BoxCategory.entries, { it.label }, { onEdit(draft.copy(category = it)) }, enabled = !state.busy)
        FoodRescueTextField("Mô tả hộp", draft.description, { onEdit(draft.copy(description = it)) }, enabled = !state.busy,
            placeholder = "Loại thực phẩm và đặc điểm của hộp", error = error(BoxField.DESCRIPTION))
        FoodRescueTextField("Thông tin dị ứng", draft.allergens, { onEdit(draft.copy(allergens = it)) }, enabled = !state.busy,
            placeholder = "Ví dụ: Có sữa, trứng và lúa mì", error = error(BoxField.ALLERGENS))
        HorizontalDivider()
        Text("Giá trị dành cho khách", style = MaterialTheme.typography.titleLarge)
        FoodRescueTextField("Giá trị tối thiểu (đ)", draft.minimumValue, { onEdit(draft.copy(minimumValue = it)) }, enabled = !state.busy,
            placeholder = "120000", error = error(BoxField.VALUE), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        FoodRescueTextField("Giá bán hộp (đ)", draft.price, { onEdit(draft.copy(price = it)) }, enabled = !state.busy,
            placeholder = "49000", error = error(BoxField.PRICE), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        if (draft.id != null) Text("Thay đổi mẫu áp dụng cho đợt bán mới. Các đợt đã tạo giữ nguyên thông tin.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
