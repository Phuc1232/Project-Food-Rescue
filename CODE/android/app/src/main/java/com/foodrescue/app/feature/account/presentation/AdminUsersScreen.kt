package com.foodrescue.app.feature.account.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.feature.account.domain.*

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun AdminUsersScreen(state: AdminUsersState, onSearch: (String) -> Unit, onRole: (AccountRole?) -> Unit,
    onStatus: (AccountStatus?) -> Unit, onOpen: (String) -> Unit, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("TÀI KHOẢN & TRUY CẬP", style = MaterialTheme.typography.labelMedium)
                    Text("Quản lý người dùng", style = MaterialTheme.typography.headlineMedium)
                    Text("Tài khoản, liên kết cửa hàng và trạng thái truy cập.", style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .25f))
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Column(Modifier.weight(1f)) { Text("${state.accounts.size}", style = MaterialTheme.typography.headlineLarge); Text("Tài khoản", style = MaterialTheme.typography.labelMedium) }
                        Column(Modifier.weight(1f)) { Text("${state.suspendedCount}", style = MaterialTheme.typography.headlineLarge); Text("Tạm khóa", style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
        }
        item { FoodRescueTextField("Tìm tài khoản", state.query, onSearch, placeholder = "Tên, email, điện thoại, mã",
            trailingIcon = { if (state.query.isNotBlank()) IconButton({ onSearch("") }) {
                FoodRescueGlyph(FoodRescueIcon.CLOSE, description = "Xóa tìm tài khoản")
            } else FoodRescueGlyph(FoodRescueIcon.SEARCH) }) }
        item {
            Text("Loại tài khoản", style = MaterialTheme.typography.labelMedium)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(state.role == null, { onRole(null) }, label = { Text("Tất cả · ${state.accounts.size}") })
                AccountRole.entries.forEach { role ->
                    FilterChip(state.role == role, { onRole(role) },
                        label = { Text("${role.label} · ${state.countForRole(role)}") })
                }
            }
            Text("Trạng thái tài khoản", style = MaterialTheme.typography.labelMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(state.status == null, { onStatus(null) }, label = { Text("Tất cả trạng thái") })
                AccountStatus.entries.forEach { FilterChip(state.status == it, { onStatus(it) }, label = { Text(it.label) }) }
            }
        }
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${state.visible.size} kết quả", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            TextButton(onRefresh, enabled = !state.busy) { Text("Làm mới") }
        } }
        if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.error?.let { item { AccountError(it) } }
        if (!state.loading && state.error == null && state.visible.isEmpty()) item {
            FoodRescueCard {
                FoodRescueGlyph(FoodRescueIcon.PEOPLE)
                val emptyGroup = state.role?.let { state.countForRole(it) == 0 } == true &&
                    state.query.isBlank() && state.status == null
                Text(if (emptyGroup) "Chưa có tài khoản thuộc nhóm này" else "Không tìm thấy tài khoản",
                    style = MaterialTheme.typography.titleMedium)
                if (!emptyGroup) Text("Thử đổi từ khóa hoặc bộ lọc để tìm người dùng.")
            }
        }
        items(state.visible, key = { it.key }) { user ->
            Surface(onClick = { onOpen(user.key) }, enabled = !state.busy, shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                Text(user.name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(user.name, style = MaterialTheme.typography.titleMedium)
                            Text(user.email ?: user.reference, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FoodRescueGlyph(FoodRescueIcon.CHEVRON)
                    }
                    AccountValue("Loại tài khoản", user.primaryRole.label)
                    AccountStatusBadge(user.record.status)
                    user.relationships.forEach { link ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(link.storeName, style = MaterialTheme.typography.labelMedium)
                            Text("${link.statusLabel}: ${link.status}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (user.protected) Text("Tài khoản quản trị được bảo vệ", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
@Composable
internal fun AccountStatusBadge(status: AccountStatus, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Trạng thái tài khoản", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = RoundedCornerShape(10.dp),
            color = if (status == AccountStatus.SUSPENDED) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer) {
            Text(status.label, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
        }
    }
}
@Composable
internal fun AccountError(message: String, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(14.dp)) {
        Text(message, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
    }
}
@Composable
internal fun AccountValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

