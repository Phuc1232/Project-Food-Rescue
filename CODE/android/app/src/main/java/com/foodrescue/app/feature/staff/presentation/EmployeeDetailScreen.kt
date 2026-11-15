package com.foodrescue.app.feature.staff.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.feature.staff.domain.*

@Composable
internal fun EmployeeDetailScreen(storeName: String, employee: StoreEmployee, canEdit: Boolean, onBack: () -> Unit,
    onEdit: () -> Unit, onAccess: () -> Unit, onRetry: () -> Unit, error: String?, modifier: Modifier = Modifier) {
    FoodRescuePage("Hồ sơ nhân viên", onBack, modifier, footer = {
        if (employee.status == EmployeeStatus.ACTIVE) FoodRescueButton("Chỉnh sửa hồ sơ và quyền", onEdit, Modifier.fillMaxWidth(), enabled = canEdit)
        else FoodRescueButton("Khôi phục truy cập", onAccess, Modifier.fillMaxWidth(), enabled = canEdit)
    }) {
        Surface(shape = RoundedCornerShape(24.dp), color = EmployeeForest) {
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                EmployeeAvatar(employee.name)
                Text(employee.name, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Text(storeName, style = MaterialTheme.typography.bodyMedium, color = EmployeeMint)
                EmployeeStatusBadge(employee.status)
            }
        }
        EmployeeError(error)
        if (error != null) TextButton(onRetry) { Text("Tải lại") }
        FoodRescueCard {
            Text("Thông tin tài khoản", style = MaterialTheme.typography.titleMedium)
            EmployeeValue("Email đăng nhập", employee.email)
            EmployeeValue("Số điện thoại", employee.phone.ifBlank { "Chưa cập nhật" })
            EmployeeValue("Ngày tham gia", employeeDate(employee.createdAtMillis))
        }
        FoodRescueCard {
            Text(if (employee.status == EmployeeStatus.ACTIVE) "Quyền đang có hiệu lực" else "Quyền trước khi thu hồi", style = MaterialTheme.typography.titleMedium)
            Text("${employeeRole(employee.permissions)} · ${employee.permissions.size} quyền", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            EmployeePermissionSummary(employee.permissions)
            if (employee.status == EmployeeStatus.REVOKED) Text("Tài khoản hiện không có quyền truy cập cửa hàng.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        Text("Lịch sử truy cập", style = MaterialTheme.typography.titleMedium)
        FoodRescueCard {
            employee.events.asReversed().forEachIndexed { index, event ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    FoodRescueGlyph(if (event.type == EmployeeEventType.REVOKED) FoodRescueIcon.CLOSE else FoodRescueIcon.CHECK,
                        tint = if (event.type == EmployeeEventType.REVOKED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(event.type.label, style = MaterialTheme.typography.titleSmall)
                        Text("Chủ cửa hàng · ${employeeDate(event.atMillis)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (event.note.isNotBlank()) Text(event.note, style = MaterialTheme.typography.bodyMedium)
                        Text(event.permissions.joinToString(" · ") { it.label }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (employee.status == EmployeeStatus.ACTIVE) {
            HorizontalDivider()
            Text("Kiểm soát truy cập", style = MaterialTheme.typography.titleMedium)
            Text("Thu hồi khi nhân viên ngừng làm việc. Hồ sơ và lịch sử được giữ lại.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onAccess, enabled = canEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Thu hồi truy cập") }
        }
    }
}
