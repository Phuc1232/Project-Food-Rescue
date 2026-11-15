package com.foodrescue.app.feature.staff.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.feature.staff.domain.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EmployeeListScreen(storeName: String, state: EmployeeUiState, onBack: () -> Unit, onCreate: () -> Unit,
    onSearch: (String) -> Unit, onFilter: (EmployeeFilter) -> Unit, onOpen: (String) -> Unit, onRetry: () -> Unit,
    onReset: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Scaffold(modifier, containerColor = colors.background, topBar = {
        TopAppBar(title = { Text("Nhân viên", style = MaterialTheme.typography.titleMedium) }, navigationIcon = {
            IconButton(onBack) { FoodRescueGlyph(FoodRescueIcon.BACK, description = "Quay lại") }
        }, actions = { TextButton(onRetry, enabled = !state.busy) { Text("Tải lại") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background))
    }, bottomBar = {
        Surface(color = colors.surface, shadowElevation = 4.dp) {
            FoodRescueButton("Thêm nhân viên", onCreate, Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 24.dp, vertical = 12.dp), enabled = state.canMutate)
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Surface(color = EmployeeForest, shape = RoundedCornerShape(26.dp)) {
                    Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("NHÂN SỰ & PHÂN QUYỀN", style = MaterialTheme.typography.labelSmall, color = EmployeeMint)
                        Text("Đội ngũ cửa hàng", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                        Text(storeName, style = MaterialTheme.typography.bodyMedium, color = EmployeeMint)
                        HorizontalDivider(color = Color.White.copy(alpha = .2f))
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(if (state.loading || state.loadError != null) "—" else state.activeCount.toString(), style = MaterialTheme.typography.headlineLarge, color = Color.White)
                                Text("Đang hoạt động", style = MaterialTheme.typography.labelMedium, color = EmployeeMint)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(if (state.loading || state.loadError != null) "—" else (state.employees.size - state.activeCount).toString(), style = MaterialTheme.typography.headlineLarge, color = Color.White)
                                Text("Đã thu hồi", style = MaterialTheme.typography.labelMedium, color = EmployeeMint)
                            }
                        }
                    }
                }
            }
            item { FoodRescueTextField("Tìm nhân viên", state.query, onSearch, placeholder = "Tên, email hoặc số điện thoại",
                trailingIcon = { FoodRescueGlyph(FoodRescueIcon.SEARCH) }) }
            item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(EmployeeFilter.entries) { filter -> FilterChip(state.filter == filter, { onFilter(filter) }, label = { Text(filter.label) }) }
            } }
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (state.loadError != null) item { FoodRescueCard {
                EmployeeError(state.loadError)
                TextButton(onRetry) { Text("Thử lại") }
            } }
            if (!state.loading && state.loadError == null) {
                item { Text("${state.visible.size} nhân viên", style = MaterialTheme.typography.titleMedium) }
                if (state.visible.isEmpty()) item { FoodRescueCard {
                    FoodRescueGlyph(FoodRescueIcon.PEOPLE, tint = colors.primary)
                    Text(if (state.employees.isEmpty()) "Xây dựng đội ngũ đầu tiên" else "Không có nhân viên phù hợp", style = MaterialTheme.typography.titleLarge)
                    Text(if (state.employees.isEmpty()) "Thêm nhân viên để phân công chuẩn bị và bàn giao hộp tại cửa hàng."
                        else "Thử từ khóa hoặc trạng thái khác.", color = colors.onSurfaceVariant)
                    if (state.employees.isNotEmpty()) TextButton(onReset) { Text("Xóa bộ lọc") }
                } }
                items(state.visible, key = { it.id }) { member ->
                    Surface(onClick = { onOpen(member.id) }, color = colors.surface, shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.dp, colors.outlineVariant)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                EmployeeAvatar(member.name)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(member.name, style = MaterialTheme.typography.titleMedium)
                                    Text(employeeRole(member.permissions), style = MaterialTheme.typography.labelMedium, color = colors.primary)
                                }
                                FoodRescueGlyph(FoodRescueIcon.ARROW, tint = colors.outline)
                            }
                            Text(member.email, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                            EmployeeStatusBadge(member.status)
                            HorizontalDivider(color = colors.outlineVariant)
                            Text(if (member.status == EmployeeStatus.ACTIVE) "${member.permissions.size} quyền được cấp" else "Truy cập cửa hàng đã bị chặn",
                                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
