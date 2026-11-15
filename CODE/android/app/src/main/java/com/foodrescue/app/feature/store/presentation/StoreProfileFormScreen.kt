package com.foodrescue.app.feature.store.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.feature.store.domain.*
import com.foodrescue.app.feature.store.presentation.component.*

@Composable
internal fun StoreProfileFormScreen(state: StoreManagementState, email: String, registering: Boolean,
    onEdit: (StoreField, String) -> Unit, onAccept: (Boolean) -> Unit, onNext: () -> Unit,
    onBack: () -> Unit, onSave: () -> Unit, onSaveDraft: () -> Unit, modifier: Modifier = Modifier,
    onChooseLocation: () -> Unit = {}) {
    val review = registering && state.step == 2
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun finishInput(action: () -> Unit) { focus.clearFocus(); keyboard?.hide(); action() }
    FoodRescuePage(if (registering) "Đăng ký đối tác" else "Chỉnh sửa cửa hàng", onBack, modifier, footer = {
        if (state.errors.isNotEmpty() || state.acceptanceError) {
            Text(if (state.acceptanceError) "Vui lòng xác nhận thông tin trước khi gửi." else "Kiểm tra lại các thông tin được đánh dấu.",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp))
        }
        FoodRescueButton(if (state.busy) "Đang lưu…" else if (!registering) "Lưu thay đổi" else if (review) "Gửi hồ sơ" else "Tiếp tục",
            { finishInput(if (registering && !review) onNext else onSave) }, Modifier.fillMaxWidth(), enabled = !state.busy)
        if (registering && !review) TextButton(onClick = { finishInput(onSaveDraft) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Lưu nháp và quay lại") }
    }) {
        if (registering) {
            val titles = listOf("Cửa hàng của bạn", "Liên hệ & địa điểm", "Kiểm tra hồ sơ")
            Text("BƯỚC " + (state.step + 1) + " / 3", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            Text(titles[state.step], style = MaterialTheme.typography.headlineMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { index -> Surface(Modifier.weight(1f).height(4.dp), shape = RoundedCornerShape(4.dp),
                    color = if (index <= state.step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant) {} }
            }
        }
        StoreErrorMessage(state.error)
        if (registering && !review) Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                FoodRescueGlyph(FoodRescueIcon.PERSON, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text("Email đăng nhập", style = MaterialTheme.typography.labelMedium)
                    Text(email, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (review) {
            FoodRescueCard { StoreSectionTitle(state.draft.name); StoreProfileValues(state.draft, email) }
            Row(Modifier.fillMaxWidth().toggleable(state.accepted, enabled = !state.busy, role = Role.Checkbox, onValueChange = onAccept)
                .padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                Checkbox(state.accepted, null)
                Text("Tôi xác nhận thông tin cung cấp là chính xác và đồng ý gửi hồ sơ đăng ký đối tác FoodRescue.",
                    Modifier.weight(1f).padding(start = 12.dp, top = 10.dp), style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            if (!registering || state.step == 0) FoodRescueCard {
                StoreSectionTitle("Thông tin cửa hàng")
                StoreFormField(StoreField.NAME, state.draft.name, state, onEdit)
                StoreChoiceField(StoreField.CATEGORY.label, state.draft.category, storeCategories,
                    { onEdit(StoreField.CATEGORY, it) }, enabled = !state.busy)
                OutlinedTextField(state.draft.description, { onEdit(StoreField.DESCRIPTION, it) },
                    modifier = Modifier.fillMaxWidth(), label = { Text(StoreField.DESCRIPTION.label) },
                    minLines = 3, maxLines = 6, enabled = !state.busy, shape = RoundedCornerShape(12.dp),
                    supportingText = { Text(state.draft.description.length.toString() + "/500") })
            }
            if (!registering || state.step == 1) {
                FoodRescueCard {
                    StoreSectionTitle("Thông tin liên hệ")
                    StoreFormField(StoreField.CONTACT, state.draft.contactName, state, onEdit)
                    StoreFormField(StoreField.PHONE, state.draft.phone, state, onEdit, KeyboardType.Phone)
                    StoreFormField(StoreField.EMAIL, state.draft.contactEmail, state, onEdit, KeyboardType.Email)
                    if (!registering) StoreValue("Email đăng nhập", email)
                }
                FoodRescueCard {
                    StoreSectionTitle("Địa điểm nhận hàng")
                    StoreFormField(StoreField.ADDRESS, state.draft.address, state, onEdit)
                    StoreFormField(StoreField.AREA, state.draft.area, state, onEdit)
                    StoreValue("Tỉnh / thành phố", "TP. Hồ Chí Minh")
                    StoreValue("Vị trí trên bản đồ", if (state.draft.location == null) "Chưa chọn vị trí" else "Đã ghim vị trí cửa hàng")
                    if (StoreField.LOCATION in state.errors) Text(StoreField.LOCATION.errorText,
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { finishInput(onChooseLocation) }, enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth()) {
                        FoodRescueGlyph(FoodRescueIcon.PIN)
                        Text(if (state.draft.location == null) "Chọn vị trí trên bản đồ" else "Điều chỉnh vị trí", Modifier.padding(start = 8.dp))
                    }
                }
                FoodRescueCard {
                    StoreSectionTitle("Giờ hoạt động")
                    StoreFormField(StoreField.OPEN, state.draft.opensAt, state, onEdit, KeyboardType.Text)
                    StoreFormField(StoreField.CLOSE, state.draft.closesAt, state, onEdit, KeyboardType.Text)
                    if (state.draft.closesAt < state.draft.opensAt) Text("Đóng cửa vào ngày hôm sau.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StoreFormField(field: StoreField, value: String, state: StoreManagementState,
    onEdit: (StoreField, String) -> Unit, keyboard: KeyboardType = KeyboardType.Text) {
    FoodRescueTextField(field.label, value, { onEdit(field, it) },
        enabled = !state.busy, error = if (field in state.errors) field.errorText else null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Next))
}

@Composable
internal fun StoreChoiceField(label: String, value: String, choices: List<String>, onChoose: (String) -> Unit,
    enabled: Boolean = true, error: String? = null) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(14.dp)) {
                Text(value.ifBlank { "Chọn " + label.lowercase() }, Modifier.weight(1f))
                FoodRescueGlyph(FoodRescueIcon.CHEVRON)
            }
            DropdownMenu(expanded && enabled, { expanded = false }, Modifier.heightIn(max = 320.dp)) {
                choices.forEach { item -> DropdownMenuItem(text = { Text(item) }, onClick = { onChoose(item); expanded = false }) }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}
