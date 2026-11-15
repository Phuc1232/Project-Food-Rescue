package com.foodrescue.app.feature.staff.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.feature.staff.domain.EmployeeStatus

/** Account access boundary; operational staff workflows live in the future staff dashboard. */
@Composable
internal fun StaffAccessScreen(state: StaffAccessUiState, onRetry: () -> Unit, onCustomer: () -> Unit,
    onLogout: () -> Unit, modifier: Modifier = Modifier) {
    FoodRescuePage("Tài khoản nhân viên", onCustomer, modifier, footer = {
        OutlinedButton(onLogout, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Đăng xuất") }
    }) {
        val access = state.access
        when {
            state.loading -> CircularProgressIndicator()
            state.error != null -> { EmployeeError(state.error); FoodRescueButton("Thử lại", onRetry) }
            access == null -> FoodRescueEmptyState("Chưa có quyền truy cập", "Liên hệ chủ cửa hàng để kiểm tra tài khoản nhân viên.", "Kiểm tra lại", onRetry)
            !access.allowed -> {
                FoodRescueCard {
                    EmployeeAvatar(access.employee.name)
                    Text(if (access.employee.status == EmployeeStatus.REVOKED) "Quyền truy cập đã thu hồi" else "Cửa hàng chưa sẵn sàng", style = MaterialTheme.typography.headlineSmall)
                    Text(access.storeName)
                    Text("Liên hệ chủ cửa hàng để kiểm tra quyền truy cập của bạn.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onRetry) { Text("Kiểm tra lại") }
                }
            }
            else -> {
                FoodRescueCard {
                    EmployeeAvatar(access.employee.name)
                    Text(access.employee.name, style = MaterialTheme.typography.headlineSmall)
                    Text(access.storeName, color = MaterialTheme.colorScheme.primary)
                    EmployeeStatusBadge(access.employee.status)
                    EmployeeValue("Email đăng nhập", access.employee.email)
                }
                FoodRescueCard {
                    Text("Quyền truy cập của bạn", style = MaterialTheme.typography.titleMedium)
                    EmployeePermissionSummary(access.permissions)
                }
            }
        }
        TextButton(onCustomer, Modifier.fillMaxWidth()) { Text("Khám phá với tư cách khách hàng") }
    }
}
