package com.foodrescue.app.feature.staff.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.core.util.money
import com.foodrescue.app.feature.menu.domain.MenuStatus
import com.foodrescue.app.feature.menu.domain.MenuUnit
import com.foodrescue.app.feature.menu.presentation.component.MenuPhoto
import com.foodrescue.app.feature.surprisebox.domain.*
import com.foodrescue.app.feature.surprisebox.presentation.component.*

@Composable
internal fun StaffPackingScreen(state: StaffPackingUiState, onBack: () -> Unit, onQuantity: (String, String) -> Unit,
    onSearch: (String) -> Unit, onSafety: (PackingDraft) -> Unit, onNext: () -> Unit, onComplete: () -> Unit,
    onUpdateMenu: () -> Unit, onRetry: () -> Unit, onExit: () -> Unit, onDiscard: () -> Unit, modifier: Modifier = Modifier) {
    val draft = state.draft
    val batch = state.batch
    val enabled = state.canPrepare && !state.busy
    FoodRescuePage("Đóng gói Surprise Box", onBack, modifier, footer = {
        FoodRescueButton(if (state.busy) "Đang lưu hộp…" else if (state.step == 2) "Hoàn tất đóng gói" else "Tiếp tục",
            if (state.step == 2) onComplete else onNext, Modifier.fillMaxWidth(), enabled = if (state.step == 2) state.canComplete else enabled)
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("BƯỚC ${state.step + 1} / 3", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            TextButton(onExit, enabled = !state.busy) { Text("Lưu và thoát") }
        }
        LinearProgressIndicator(progress = { (state.step + 1) / 3f }, modifier = Modifier.fillMaxWidth().height(5.dp))
        Text(listOf("Chọn món cho hộp", "Kiểm tra trước khi đóng", "Sẵn sàng lên kệ")[state.step], style = MaterialTheme.typography.headlineMedium)
        Text(batch?.template?.name ?: "Đợt bán không còn khả dụng", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onRetry, enabled = !state.busy) { Text("Tải lại dữ liệu") } }
        state.issues.forEach { Text(it.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        if (PackingIssue.MENU_CHANGED in state.validation) FoodRescueCard {
            Text(PackingIssue.MENU_CHANGED.message, color = MaterialTheme.colorScheme.error)
            OutlinedButton(onUpdateMenu, enabled = enabled) { Text("Cập nhật món đã chọn") }
        }
        PackingValueCard(state)
        when (state.step) {
            0 -> {
                if (batch != null) FoodRescueCard {
                    Text("Yêu cầu của mẫu hộp", style = MaterialTheme.typography.titleSmall)
                    Text(batch.template.description, style = MaterialTheme.typography.bodyMedium)
                    Text("Dị ứng: ${batch.template.allergens}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Kết thúc nhận: ${boxDateTime(batch.pickupEnd)}", style = MaterialTheme.typography.bodySmall)
                }
                FoodRescueTextField("Tìm món trong menu", state.query, onSearch, placeholder = "Tên món", enabled = enabled)
                val items = state.workspace.menu.filter { it.status == MenuStatus.ACTIVE && state.query.boxSearchKey() in it.name.boxSearchKey() }
                if (items.isEmpty()) Text("Không có món phù hợp trong menu đang bán.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                items.forEach { item ->
                    val quantity = draft.selections.firstOrNull { it.itemId == item.id }?.quantity.orEmpty()
                    FoodRescueCard {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            MenuPhoto(item.photo, Modifier.size(56.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(item.name, style = MaterialTheme.typography.titleSmall)
                                Text("${money(item.price)} / ${item.unit.staffLabel}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        FoodRescueTextField("Số lượng ${item.name}", quantity, { onQuantity(item.id, it) }, enabled = enabled,
                            placeholder = if (item.unit == MenuUnit.KILOGRAM) "Ví dụ: 0,5" else "0",
                            keyboardOptions = KeyboardOptions(keyboardType = if (item.unit == MenuUnit.KILOGRAM) KeyboardType.Decimal else KeyboardType.Number),
                            error = "Số lượng chưa hợp lệ".takeIf { item.id in state.estimate.invalidIds })
                        state.estimate.lineValues[item.id]?.let { Text("Thành tiền: ${money(it)}", style = MaterialTheme.typography.labelMedium) }
                        if (quantity.isNotEmpty()) TextButton({ onQuantity(item.id, "") }, enabled = enabled) { Text("Bỏ món này") }
                    }
                }
            }
            1 -> {
                FoodRescueCard {
                    Text("Hạn dùng và bảo quản", style = MaterialTheme.typography.titleMedium)
                    Text("Hạn dùng phải bao phủ đến ${batch?.let { boxDateTime(it.pickupEnd) }.orEmpty()}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BoxDateTimeField(if (draft.expiresAt == 0L) "Chọn hạn sử dụng" else "Sử dụng trước", draft.expiresAt.takeIf { it > 0 } ?: (batch?.pickupEnd ?: state.now),
                        { onSafety(draft.copy(expiresAt = it)) }, enabled = enabled)
                    if (draft.expiresAt == 0L) Text("Chưa xác nhận hạn sử dụng", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    BoxChoice("Điều kiện bảo quản", draft.storage, listOf(null) + BoxStorage.entries, { it?.staffLabel ?: "Chọn cách bảo quản" },
                        { onSafety(draft.copy(storage = it)) }, enabled = enabled)
                }
                FoodRescueCard {
                    Text("Kiểm tra thực tế", style = MaterialTheme.typography.titleMedium)
                    PackingCheck("Món ăn đạt chất lượng", "Đủ số lượng, không có dấu hiệu hư hỏng.", draft.foodChecked, enabled) { onSafety(draft.copy(foodChecked = it)) }
                    HorizontalDivider()
                    PackingCheck("Bao bì sạch, kín và nguyên vẹn", "Đóng gói phù hợp với điều kiện bảo quản.", draft.packagingChecked, enabled) { onSafety(draft.copy(packagingChecked = it)) }
                    HorizontalDivider()
                    PackingCheck("Đã đối chiếu thành phần gây dị ứng", batch?.template?.allergens.orEmpty(), draft.allergensChecked, enabled) { onSafety(draft.copy(allergensChecked = it)) }
                }
            }
            2 -> {
                FoodRescueCard {
                    Text("Các món thực tế", style = MaterialTheme.typography.titleMedium)
                    draft.selections.forEach { selected ->
                        val item = state.workspace.menu.firstOrNull { it.id == selected.itemId }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("${selected.quantity} ${selected.unit.staffLabel} · ${item?.name.orEmpty()}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(money(state.estimate.lineValues[selected.itemId] ?: 0), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                FoodRescueCard {
                    PackingValue("Sử dụng trước", boxDateTime(draft.expiresAt))
                    PackingValue("Bảo quản", draft.storage?.staffLabel.orEmpty())
                    PackingValue("Người đóng gói và kiểm tra", state.workspace.access?.employee?.name.orEmpty())
                    HorizontalDivider()
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { FoodRescueGlyph(FoodRescueIcon.CHECK, tint = MaterialTheme.colorScheme.primary); Text("Đã kiểm tra đủ 3 mục", style = MaterialTheme.typography.titleSmall) }
                }
            }
        }
        TextButton(onDiscard, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Bỏ bản nháp", color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun PackingValueCard(state: StaffPackingUiState) {
    val total = state.estimate.total; val target = state.batch?.template?.minimumValue ?: 0
    FoodRescueCard {
        Text("GIÁ TRỊ TRONG HỘP", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(money(total), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        LinearProgressIndicator(progress = { if (target > 0) (total.toFloat() / target).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth().height(6.dp))
        Text(if (total >= target && target > 0) "Đạt mức tối thiểu ${money(target)}" else "Cần thêm ${money((target - total).coerceAtLeast(0))} để đạt ${money(target)}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable
private fun PackingCheck(title: String, description: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Checkbox(checked, null, enabled = enabled)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable
internal fun PackingValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}
