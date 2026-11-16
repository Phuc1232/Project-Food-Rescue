package com.foodrescue.app.feature.staff.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.core.util.money
import com.foodrescue.app.feature.menu.domain.MenuStatus
import com.foodrescue.app.feature.menu.presentation.component.MenuPhoto
import com.foodrescue.app.feature.menu.presentation.component.MenuStatusBadge
import com.foodrescue.app.feature.surprisebox.domain.*
import com.foodrescue.app.feature.surprisebox.presentation.component.boxDateTime

@Composable
internal fun StaffMenuScreen(state: StaffPackingUiState, onSearch: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Menu nội bộ", style = MaterialTheme.typography.headlineMedium)
        Text("${state.workspace.menu.count { it.status == MenuStatus.ACTIVE }} món đang kinh doanh", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FoodRescueTextField("Tìm món", state.query, onSearch, placeholder = "Tên món trong menu")
        val items = state.workspace.menu.filter { state.query.boxSearchKey() in it.name.boxSearchKey() }.sortedWith(compareBy({ it.status != MenuStatus.ACTIVE }, { it.name }))
        if (items.isEmpty()) FoodRescueCard {
            FoodRescueGlyph(FoodRescueIcon.RECEIPT)
            Text("Chưa có món phù hợp", style = MaterialTheme.typography.titleMedium)
            Text("Menu của cửa hàng sẽ xuất hiện khi chủ cửa hàng thêm món.", style = MaterialTheme.typography.bodyMedium)
        }
        items.forEach { item -> FoodRescueCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MenuPhoto(item.photo, Modifier.size(68.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                    Text("${money(item.price)} / ${item.unit.staffLabel}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (item.description.isNotBlank()) Text(item.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MenuStatusBadge(item.status)
        } }
    }
}

@Composable
internal fun StaffBoxesScreen(state: StaffPackingUiState, onSearch: (String) -> Unit, onOpen: (String) -> Unit,
    onFilter: (BoxCondition?) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Hộp đã đóng gói", style = MaterialTheme.typography.headlineMedium)
        Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFF133E32)) {
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("KHO HỘP THỰC TẾ", style = MaterialTheme.typography.labelSmall, color = Color(0xFFD8EDB4))
                Text("${state.workspace.boxes.count { it.isEligibleAt(state.now) }} hộp sẵn sàng", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Text("${state.workspace.boxes.count { it.conditionAt(state.now) == BoxCondition.REJECTED }} không đạt · " +
                    "${state.workspace.boxes.count { it.conditionAt(state.now) == BoxCondition.EXPIRED }} hết hạn", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD8EDB4))
            }
        }
        FoodRescueTextField("Tìm hộp", state.query, onSearch, placeholder = "Mã hộp hoặc tên mẫu")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(state.boxFilter == null, { onFilter(null) }, label = { Text("Tất cả") })
            BoxCondition.entries.forEach { condition -> FilterChip(state.boxFilter == condition, { onFilter(condition) }, label = { Text(condition.staffLabel) }) }
        }
        val boxes = state.filteredBoxes
        Text("${boxes.size} hộp", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (boxes.isEmpty()) FoodRescueCard {
            FoodRescueGlyph(FoodRescueIcon.BAG)
            Text("Chưa có hộp phù hợp", style = MaterialTheme.typography.titleMedium)
            Text("Hộp được xác nhận đóng gói sẽ lưu tại đây cùng món thực tế và hạn dùng.", style = MaterialTheme.typography.bodyMedium)
        }
        boxes.forEach { box -> PackedBoxRow(box, state.now, { onOpen(box.id) }) }
    }
}

@Composable
internal fun PackedBoxRow(box: PackedBox, now: Long, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onOpen, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(box.code, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(box.conditionAt(now).staffLabel, style = MaterialTheme.typography.labelMedium,
                    color = if (box.conditionAt(now) in setOf(BoxCondition.READY, BoxCondition.DELIVERED)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }
            Text(box.templateName, style = MaterialTheme.typography.titleSmall)
            Text("${money(box.value)} · ${box.items.size} món", style = MaterialTheme.typography.bodyMedium)
            Text("Dùng trước ${boxDateTime(box.expiresAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun PackedBoxDetailScreen(box: PackedBox, now: Long, onBack: () -> Unit, onOverview: () -> Unit, modifier: Modifier = Modifier,
    onInspect: (() -> Unit)? = null) {
    FoodRescuePage("Chi tiết hộp thực tế", onBack, modifier, footer = {
        if (onInspect != null && box.canInspect()) FoodRescueButton("Kiểm tra tình trạng hộp", onInspect, Modifier.fillMaxWidth())
        else FoodRescueButton("Về ca làm việc", onOverview, Modifier.fillMaxWidth())
    }) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFF133E32)) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FoodRescueGlyph(FoodRescueIcon.CHECK, Modifier.size(36.dp), tint = Color(0xFFD8EDB4))
                Text(when (box.conditionAt(now)) {
                    BoxCondition.READY -> "Đóng gói hoàn tất"
                    BoxCondition.RECHECK_REQUIRED -> "Chờ kiểm tra lại"
                    BoxCondition.REJECTED -> "Không đạt"
                    BoxCondition.EXPIRED -> "Đã hết hạn"
                    BoxCondition.DELIVERED -> "Đã bàn giao"
                }, style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Text(box.code, style = MaterialTheme.typography.titleMedium, color = Color(0xFFD8EDB4))
                Text(box.templateName, style = MaterialTheme.typography.bodyMedium, color = Color.White)
            }
        }
        box.handover?.let { receipt -> FoodRescueCard {
            PackingValue("Đã giao cho đơn", receipt.reference)
            PackingValue("Thời gian bàn giao", boxDateTime(receipt.atMillis))
        } }
        if (box.handover == null && now >= box.expiresAt) Text("Hộp đã hết hạn sử dụng, không được bàn giao.", color = MaterialTheme.colorScheme.error)
        if (box.conditionAt(now) == BoxCondition.REJECTED) Text("Hộp đã bị loại khỏi bàn giao. Đóng hộp thay thế nếu đợt bán còn thiếu.", color = MaterialTheme.colorScheme.error)
        FoodRescueCard {
            Text("Thành phần thực tế", style = MaterialTheme.typography.titleMedium)
            box.items.forEach { item ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${item.quantity} ${item.unit.staffLabel} · ${item.name}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(money(item.value), style = MaterialTheme.typography.labelLarge)
                }
            }
            HorizontalDivider()
            PackingValue("Tổng giá trị niêm yết", money(box.value))
            Text("Mức tối thiểu ${money(box.minimumValue)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FoodRescueCard {
            PackingValue("Sử dụng trước", boxDateTime(box.expiresAt))
            PackingValue("Bảo quản", box.storage.staffLabel)
            PackingValue("Thành phần gây dị ứng", box.allergens)
        }
        FoodRescueCard {
            PackingValue("Người đóng gói và kiểm tra", box.preparedByName)
            PackingValue("Hoàn tất lúc", boxDateTime(box.packedAt))
            Text("Đã xác nhận chất lượng món, bao bì và thành phần gây dị ứng.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BoxInspectionHistory(box)
    }
}

@Composable
internal fun StaffAccountScreen(state: StaffPackingUiState, onCustomer: () -> Unit, onLogout: () -> Unit, modifier: Modifier = Modifier) {
    val access = state.workspace.access ?: return
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Tài khoản", style = MaterialTheme.typography.headlineMedium)
        FoodRescueCard {
            EmployeeAvatar(access.employee.name)
            Text(access.employee.name, style = MaterialTheme.typography.titleLarge)
            Text(access.storeName, color = MaterialTheme.colorScheme.primary)
            PackingValue("Email đăng nhập", access.employee.email)
            EmployeeStatusBadge(access.employee.status)
        }
        FoodRescueCard { Text("Quyền được phân công", style = MaterialTheme.typography.titleMedium); EmployeePermissionSummary(access.permissions) }
        OutlinedButton(onCustomer, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Khám phá với tư cách khách hàng") }
        TextButton(onLogout, Modifier.fillMaxWidth()) { Text("Đăng xuất", color = MaterialTheme.colorScheme.error) }
    }
}
