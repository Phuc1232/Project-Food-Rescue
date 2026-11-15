package com.foodrescue.app.feature.surprisebox.presentation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.core.util.money
import com.foodrescue.app.feature.store.presentation.component.*
import com.foodrescue.app.feature.surprisebox.domain.*
import com.foodrescue.app.feature.surprisebox.presentation.component.*

@Composable
internal fun OwnerBoxesScreen(storeName: String, state: OwnerBoxesUiState, onSection: (OwnerBoxSection) -> Unit,
    onSearch: (String) -> Unit, onFilter: (BatchStatus?) -> Unit, onCreateTemplate: () -> Unit, onEditTemplate: (BoxTemplate) -> Unit,
    onToggleTemplate: (String) -> Unit, onCreateBatch: (String?) -> Unit, onOpenBatch: (String) -> Unit, onMenu: () -> Unit,
    modifier: Modifier = Modifier) {
    val batches = state.section == OwnerBoxSection.BATCHES
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Text("Hộp của cửa hàng", style = MaterialTheme.typography.headlineLarge)
            Text(storeName, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            StoreHeroCard("SURPRISE BOX", "${state.available} hộp sẵn sàng") {
                Text("${state.liveBatches.size} đợt đang bán · ${state.catalog.batches.count { it.statusAt(state.now) == BatchStatus.SCHEDULED }} đợt đã lên lịch",
                    style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD9EBE0))
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(batches, { onSection(OwnerBoxSection.BATCHES) }, label = { Text("Đợt bán") })
                FilterChip(!batches, { onSection(OwnerBoxSection.TEMPLATES) }, label = { Text("Mẫu hộp") })
                AssistChip(onMenu, label = { Text("Menu nội bộ") })
            }
            FoodRescueButton(if (batches) "Tạo đợt bán" else "Tạo mẫu hộp", { if (batches) onCreateBatch(null) else onCreateTemplate() },
                Modifier.fillMaxWidth().padding(top = 12.dp), enabled = !state.busy)
        }
        item {
            FoodRescueTextField("Tìm hộp", state.query, onSearch, placeholder = "Tên mẫu hộp", trailingIcon = { FoodRescueGlyph(FoodRescueIcon.SEARCH) })
            if (batches) Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(state.filter == null, { onFilter(null) }, label = { Text("Tất cả") })
                BatchStatus.entries.forEach { status -> FilterChip(state.filter == status, { onFilter(status) }, label = { Text(status.label) }) }
            }
        }
        item { StoreErrorMessage(state.error) }
        if (batches) {
            item { Text("${state.visibleBatches.size} đợt bán", style = MaterialTheme.typography.titleMedium) }
            if (state.visibleBatches.isEmpty()) item {
                StoreEmptyPanel(FoodRescueIcon.BAG, if (state.catalog.batches.isEmpty()) "Sẵn sàng cho đợt bán đầu tiên" else "Không tìm thấy đợt bán",
                    if (state.catalog.templates.none { it.active }) "Tạo mẫu hộp với giá trị, giá bán và thông tin thực phẩm của cửa hàng."
                    else if (state.catalog.batches.isEmpty()) "Chọn mẫu hộp, đặt số lượng và khung giờ nhận hàng." else "Thử tên khác hoặc chọn lại trạng thái.")
                if (state.catalog.templates.none { it.active }) TextButton(onClick = onCreateTemplate) { Text("Tạo mẫu hộp đầu tiên") }
            }
            items(state.visibleBatches, key = { it.id }) { batch ->
                Surface(onClick = { onOpenBatch(batch.id) }, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BatchStatusBadge(batch.statusAt(state.now))
                            Spacer(Modifier.weight(1f))
                            FoodRescueGlyph(FoodRescueIcon.ARROW, tint = MaterialTheme.colorScheme.primary)
                        }
                        Text(batch.template.name, style = MaterialTheme.typography.titleLarge)
                        Text("${money(batch.template.price)} / hộp", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text("Nhận từ ${boxDateTime(batch.pickupStart)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        BoxStock(batch)
                    }
                }
            }
        } else {
            item { Text("${state.visibleTemplates.size} mẫu hộp", style = MaterialTheme.typography.titleMedium) }
            if (state.visibleTemplates.isEmpty()) item {
                StoreEmptyPanel(FoodRescueIcon.BAG, if (state.catalog.templates.isEmpty()) "Một mẫu hộp, nhiều đợt bán" else "Không tìm thấy mẫu hộp",
                    if (state.catalog.templates.isEmpty()) "Lưu thông tin chung của hộp để nhanh chóng mở bán mỗi ngày." else "Thử tìm bằng tên khác.")
            }
            items(state.visibleTemplates, key = { it.id }) { template ->
                FoodRescueCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StoreIconTile(FoodRescueIcon.BAG)
                        Column(Modifier.weight(1f)) {
                            Text(template.category.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(template.name, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                    Text(if (template.active) "Đang sử dụng" else "Ngừng sử dụng", style = MaterialTheme.typography.labelMedium,
                        color = if (template.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        BoxMetric("Giá bán / hộp", money(template.price), Modifier.weight(1f))
                        BoxMetric("Giá trị tối thiểu", money(template.minimumValue), Modifier.weight(1f))
                    }
                    Text(template.description, style = MaterialTheme.typography.bodyMedium)
                    Text("Dị ứng: ${template.allergens}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (template.active) FoodRescueButton("Tạo đợt từ mẫu này", { onCreateBatch(template.id) }, Modifier.fillMaxWidth(), enabled = !state.busy)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton({ onEditTemplate(template) }, enabled = !state.busy) { Text("Sửa mẫu") }
                        TextButton({ onToggleTemplate(template.id) }, enabled = !state.busy) { Text(if (template.active) "Ngừng dùng" else "Mở lại mẫu") }
                    }
                }
            }
        }
    }
}
