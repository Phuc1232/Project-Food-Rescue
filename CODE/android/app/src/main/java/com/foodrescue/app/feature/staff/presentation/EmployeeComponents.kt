package com.foodrescue.app.feature.staff.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.feature.staff.domain.*

@Composable
internal fun EmployeeAvatar(name: String, modifier: Modifier = Modifier) {
    val initials = name.trim().split(Regex("\\s+")).filter(String::isNotBlank).takeLast(2).map { it.first().uppercaseChar() }.joinToString("")
    Surface(modifier.size(50.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
        Box(contentAlignment = Alignment.Center) { Text(initials, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
    }
}
@Composable
internal fun EmployeeStatusBadge(status: EmployeeStatus, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier, shape = RoundedCornerShape(9.dp), color = if (status == EmployeeStatus.ACTIVE) colors.primaryContainer else colors.surfaceContainerHigh) {
        Text(status.label, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium,
            color = if (status == EmployeeStatus.ACTIVE) colors.onPrimaryContainer else colors.onSurfaceVariant)
    }
}
@Composable
internal fun EmployeePermissionPicker(permissions: Set<EmployeePermission>, enabled: Boolean,
    onPermission: (EmployeePermission, Boolean) -> Unit, onPreset: (Set<EmployeePermission>) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Bộ quyền gợi ý", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Nhận hàng" to pickupPermissions, "Vận hành" to operationsPermissions).forEach { (title, preset) ->
                val selected = permissions == preset
                Surface(onClick = { onPreset(preset) }, enabled = enabled, modifier = Modifier.weight(1f).fillMaxHeight(),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FoodRescueGlyph(if (selected) FoodRescueIcon.CHECK else FoodRescueIcon.PEOPLE, tint = MaterialTheme.colorScheme.primary)
                        Text(title, style = MaterialTheme.typography.titleSmall)
                        Text("${preset.size} quyền", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Text("Quyền chi tiết", style = MaterialTheme.typography.titleMedium)
        EmployeePermission.entries.forEach { permission ->
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Row(Modifier.fillMaxWidth().toggleable(permission in permissions, enabled = enabled, role = Role.Switch,
                    onValueChange = { onPermission(permission, it) }).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(permission.label, style = MaterialTheme.typography.titleSmall)
                        Text(permission.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(permission in permissions, onCheckedChange = null, enabled = enabled)
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .55f), shape = RoundedCornerShape(16.dp)) {
            Text("Đối soát, tài khoản ngân hàng và quản lý nhân viên chỉ dành cho chủ cửa hàng.", Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}
@Composable
internal fun EmployeePermissionSummary(permissions: Set<EmployeePermission>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EmployeePermission.entries.filter { it in permissions }.forEach { permission ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                FoodRescueGlyph(FoodRescueIcon.CHECK, tint = MaterialTheme.colorScheme.primary)
                Text(permission.label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
@Composable
internal fun EmployeeError(error: String?, modifier: Modifier = Modifier) {
    if (error != null) Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
        Text(error, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
    }
}
@Composable
internal fun EmployeeValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
internal val EmployeeForest = Color(0xFF123D32)
internal val EmployeeMint = Color(0xFFD3E9D9)
