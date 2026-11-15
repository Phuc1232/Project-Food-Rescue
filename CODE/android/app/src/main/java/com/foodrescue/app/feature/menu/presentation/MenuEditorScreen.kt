package com.foodrescue.app.feature.menu.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.feature.menu.domain.*
import com.foodrescue.app.feature.menu.presentation.component.*

@Composable
internal fun MenuEditorScreen(state: MenuUiState, onBack: () -> Unit, onSave: () -> Unit,
    onName: (String) -> Unit, onPrice: (String) -> Unit, onDescription: (String) -> Unit,
    onCategory: (MenuCategory) -> Unit, onUnit: (MenuUnit) -> Unit, onPhoto: () -> Unit, onRemovePhoto: () -> Unit,
    modifier: Modifier = Modifier) {
    val draft = state.draft
    val focus = LocalFocusManager.current
    FoodRescuePage(if (draft.id == null) "Thêm món" else "Chỉnh sửa món", onBack, modifier, footer = {
        if (state.errors.isNotEmpty()) Text("Kiểm tra các thông tin được đánh dấu bên trên.", color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
        FoodRescueButton(if (state.busy) "Đang xử lý…" else "Lưu món", { focus.clearFocus(); onSave() }, Modifier.fillMaxWidth(), enabled = !state.busy)
    }) {
        MenuError(state.error)
        FoodRescueCard {
            MenuPhoto(draft.photo, Modifier.fillMaxWidth().aspectRatio(1.7f))
            Text("Ảnh món ăn", style = MaterialTheme.typography.titleSmall)
            Text("Ảnh giúp bạn nhận diện món nhanh hơn. Tối đa 10 MB.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onPhoto, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(if (draft.photo == null) "Chọn ảnh" else "Đổi ảnh") }
            if (draft.photo != null) TextButton(onRemovePhoto, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Gỡ ảnh") }
        }
        Text("Thông tin món", style = MaterialTheme.typography.titleLarge)
        FoodRescueTextField("Tên món", draft.name, onName, enabled = !state.busy, placeholder = "Ví dụ: Bánh mì bơ tỏi",
            error = when { MenuField.NAME in state.errors -> "Nhập tên món từ 2 đến 100 ký tự."
                MenuField.DUPLICATE in state.errors -> "Món này đã có cùng nhóm và đơn vị trong menu."
                else -> null }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
        MenuChoice("Nhóm món", draft.category, MenuCategory.entries, { it.label }, onCategory, enabled = !state.busy)
        MenuChoice("Đơn vị tính", draft.unit, MenuUnit.entries, { it.label }, onUnit, enabled = !state.busy)
        FoodRescueTextField("Giá niêm yết (đ)", draft.price, onPrice, enabled = !state.busy, placeholder = "25000",
            hint = "Giá cho 1 ${draft.unit.label}. Giá Surprise Box được thiết lập riêng khi tạo hộp.",
            error = if (MenuField.PRICE in state.errors) "Nhập giá nguyên đồng từ 1 đến 999.999.999 đ." else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Mô tả món", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(draft.description, onDescription, Modifier.fillMaxWidth().semantics { contentDescription = "Mô tả món" },
                enabled = !state.busy, minLines = 3, maxLines = 6, shape = RoundedCornerShape(12.dp),
                placeholder = { Text("Thành phần, hương vị hoặc lưu ý về món") }, isError = MenuField.DESCRIPTION in state.errors)
            Text("${draft.description.length}/500", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (draft.id != null) MenuStatusBadge(draft.status)
    }
}
