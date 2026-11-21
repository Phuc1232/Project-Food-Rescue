package com.foodrescue.app.feature.account.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.feature.account.domain.*

@Composable
internal fun AdminUserDetailScreen(state: AdminUsersState, onBack: () -> Unit, onPrepare: () -> Unit,
    onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val user = state.selected ?: return
    FoodRescuePage("Chi tiết tài khoản", onBack, modifier, footer = if (user.protected) null else {{
        FoodRescueButton(
            if (user.record.status == AccountStatus.ACTIVE) "Khóa tài khoản" else "Mở khóa tài khoản",
            onPrepare, Modifier.fillMaxWidth(), enabled = !state.busy && state.error == null)
    }}) {
        Text(user.name, style = MaterialTheme.typography.headlineMedium)
        Text(user.reference, style = MaterialTheme.typography.labelMedium)
        AccountStatusBadge(user.record.status)
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { AccountError(it) }
        TextButton(onRefresh, enabled = !state.busy) { Text("Làm mới") }
        if (user.protected) FoodRescueCard {
            Text("Tài khoản được bảo vệ", style = MaterialTheme.typography.titleMedium)
            Text("Không thể khóa tài khoản quản trị tại đây.")
        }
        user.record.events.lastOrNull()?.takeIf { it.status == AccountStatus.SUSPENDED }?.let {
            AccountError("Lý do tạm khóa: ${it.reason}")
        }
        FoodRescueCard {
            Text("Thông tin tài khoản", style = MaterialTheme.typography.titleMedium)
            AccountValue("Email đăng nhập", user.email ?: "Chưa ghi nhận email đăng nhập")
            AccountValue("Số điện thoại cá nhân", user.phone.ifBlank { "Chưa cập nhật" })
            AccountValue("Ghi nhận tài khoản", accountDate(user.record.firstSeenAt))
            AccountValue("Đăng nhập gần nhất", accountDate(user.record.lastLoginAt))
            AccountValue("Loại tài khoản", user.primaryRole.label)
        }
        if (user.relationships.isNotEmpty()) {
            Text("Liên kết cửa hàng", style = MaterialTheme.typography.titleLarge)
            user.relationships.forEach { link -> FoodRescueCard {
                Text(link.storeName, style = MaterialTheme.typography.titleMedium)
                Text("CH-${kotlin.math.abs(link.storeId.toLong())}", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                AccountValue("Vai trò tại cửa hàng", link.role.label)
                AccountValue(link.statusLabel, link.status)
                if (link.permissions.isNotEmpty()) AccountValue("Quyền do cửa hàng cấp", link.permissions.joinToString(" · "))
            } }
        }
        Text("Nhật ký trạng thái", style = MaterialTheme.typography.titleLarge)
        if (user.record.events.isEmpty()) Text("Chưa có thay đổi trạng thái.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        user.record.events.asReversed().forEach { event -> FoodRescueCard {
            Text(event.status.action, style = MaterialTheme.typography.titleMedium)
            Text(accountDate(event.atMillis), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(event.reason)
            AccountValue("Người xử lý", event.actor)
        } }
    }
}

