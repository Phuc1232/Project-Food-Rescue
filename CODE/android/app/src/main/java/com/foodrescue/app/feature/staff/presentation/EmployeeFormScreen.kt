package com.foodrescue.app.feature.staff.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.feature.staff.domain.*

@Composable
internal fun EmployeeFormScreen(storeName: String, state: EmployeeUiState, onBack: () -> Unit, onName: (String) -> Unit,
    onEmail: (String) -> Unit, onPhone: (String) -> Unit, onPermission: (EmployeePermission, Boolean) -> Unit,
    onPreset: (Set<EmployeePermission>) -> Unit, onNext: () -> Unit, onSave: () -> Unit, onRetry: () -> Unit,
    modifier: Modifier = Modifier) {
    val draft = state.draft
    val creating = draft.id == null
    FoodRescuePage(if (creating) "Thêm nhân viên" else "Chỉnh sửa nhân viên", onBack, modifier, footer = {
        FoodRescueButton(if (state.busy) "Đang lưu…" else when (state.step) { 0 -> "Tiếp tục chọn quyền"; 1 -> "Xem lại thông tin"; else -> if (creating) "Tạo tài khoản" else "Lưu thay đổi" },
            if (state.step == 2) onSave else onNext, Modifier.fillMaxWidth(), enabled = state.canMutate)
    }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Thông tin", "Quyền", "Xác nhận").forEachIndexed { index, label ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(progress = { if (state.step >= index) 1f else 0f }, modifier = Modifier.fillMaxWidth())
                    Text("0${index + 1} · $label", style = MaterialTheme.typography.labelSmall,
                        color = if (state.step == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (state.loadError != null) { EmployeeError(state.loadError); TextButton(onRetry) { Text("Thử lại") } }
        EmployeeError(state.error)
        when (state.step) {
            0 -> {
                Text("Thông tin nhân viên", style = MaterialTheme.typography.headlineSmall)
                Text(storeName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FoodRescueCard {
                    FoodRescueTextField("Họ và tên", draft.name, onName, enabled = !state.busy,
                        error = EmployeeIssue.NAME.takeIf { it in state.issues }?.message,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
                    FoodRescueTextField("Email đăng nhập", draft.email, onEmail, enabled = creating && !state.busy,
                        error = EmployeeIssue.EMAIL.takeIf { it in state.issues }?.message,
                        hint = if (creating) "Email riêng của nhân viên, dùng để đăng nhập FoodRescue." else "Email được giữ cố định cho tài khoản này.",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
                    FoodRescueTextField("Số điện thoại (không bắt buộc)", draft.phone, onPhone, enabled = !state.busy,
                        error = EmployeeIssue.PHONE.takeIf { it in state.issues }?.message,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done))
                }
            }
            1 -> {
                Text("Giao đúng quyền", style = MaterialTheme.typography.headlineSmall)
                Text(draft.name, color = MaterialTheme.colorScheme.onSurfaceVariant)
                EmployeeError(EmployeeIssue.PERMISSIONS.takeIf { it in state.issues }?.message)
                EmployeePermissionPicker(draft.permissions, !state.busy, onPermission, onPreset)
            }
            else -> {
                Text(if (creating) "Sẵn sàng thêm vào đội ngũ" else "Xác nhận thay đổi", style = MaterialTheme.typography.headlineSmall)
                FoodRescueCard {
                    EmployeeAvatar(draft.name)
                    Text(draft.name.trim(), style = MaterialTheme.typography.titleLarge)
                    EmployeeValue("Email đăng nhập", employeeEmail(draft.email))
                    if (draft.phone.isNotBlank()) EmployeeValue("Số điện thoại", draft.phone.trim())
                    EmployeeValue("Cửa hàng", storeName)
                }
                FoodRescueCard {
                    Text("${employeeRole(draft.permissions)} · ${draft.permissions.size} quyền", style = MaterialTheme.typography.titleMedium)
                    EmployeePermissionSummary(draft.permissions)
                }
                Text("Quyền truy cập có hiệu lực sau khi lưu. Bạn có thể thay đổi hoặc thu hồi trong hồ sơ nhân viên.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
