package com.foodrescue.app.feature.store.presentation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.core.util.money
import com.foodrescue.app.feature.menu.presentation.component.MenuAssetPreview
import com.foodrescue.app.feature.menu.presentation.component.MenuItemCard
import com.foodrescue.app.feature.store.domain.*
import com.foodrescue.app.feature.store.presentation.component.*
import com.foodrescue.app.feature.surprisebox.domain.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StoreSupervisionDetailScreen(state: StoreSupervisionState, onBack: () -> Unit,
    onSection: (SupervisionSection) -> Unit, onDecide: (StoreRestrictionAction, String?) -> Unit,
    onBox: (String) -> Unit, onMenu: (String) -> Unit, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val detail = state.detail
    var assetId by rememberSaveable(state.selectedKey) { mutableStateOf<String?>(null) }
    Scaffold(modifier, containerColor = MaterialTheme.colorScheme.background, topBar = {
        Column {
            TopAppBar(title = { Text(detail?.entry?.reference ?: "Giám sát cửa hàng", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onBack, enabled = !state.busy) { FoodRescueGlyph(FoodRescueIcon.BACK, description = "Quay lại") } },
                actions = { TextButton(onRefresh, enabled = !state.busy) { Text("Làm mới") } })
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SupervisionSection.entries.forEach { FilterChip(state.section == it, { onSection(it) }, label = { Text(it.label) }) }
            }
        }
    }) { padding ->
        key(state.selectedKey, state.section) {
            LazyColumn(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
                contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                state.error?.let { item { StoreErrorMessage(it) } }
                if (detail != null) {
                    val store = detail.entry.store
                    item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(store.profile.name, style = MaterialTheme.typography.headlineSmall)
                        StoreOperatingBadge(store.governance)
                    } }
                    when (state.section) {
                        SupervisionSection.OVERVIEW -> {
                            item { FoodRescueCard {
                                StoreSectionTitle("Đơn đang cần phục vụ")
                                Text("${detail.paidOrders} đơn đã thanh toán", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                                Text("${detail.activeHolds} đơn đang giữ hộp", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Hạn chế quyền bán không hủy đơn đã trả tiền. Các lượt giữ hộp đã xác nhận được tiếp tục thanh toán đến khi hết thời gian giữ.", style = MaterialTheme.typography.bodySmall)
                            } }
                            item { FoodRescueCard {
                                StoreSectionTitle("Thông tin cửa hàng")
                                StoreValue("Địa chỉ", store.profile.fullAddress)
                                StoreValue("Giờ hoạt động", "${store.profile.opensAt} – ${store.profile.closesAt}")
                                StoreValue("Người đại diện", store.profile.contactName)
                                StoreValue("Liên hệ", listOf(store.profile.phone, store.profile.contactEmail).filter(String::isNotBlank).joinToString("\n"))
                            } }
                            item { FoodRescueCard {
                                StoreSectionTitle("Quyền bán trên FoodRescue")
                                store.governance.suspension?.let { Text(it.note, color = MaterialTheme.colorScheme.error); Text(reviewDate(it.atMillis), style = MaterialTheme.typography.bodySmall) }
                                Text(if (store.governance.suspension != null) "Cửa hàng đang bị ẩn khỏi khách hàng." else "Cửa hàng được phép nhận đơn mới ở các đợt bán đủ điều kiện.")
                                val action = if (store.governance.suspension == null) StoreRestrictionAction.SUSPEND_STORE else StoreRestrictionAction.RESTORE_STORE
                                OutlinedButton({ onDecide(action, null) }, Modifier.fillMaxWidth(), enabled = !state.busy && state.error == null) { Text(action.label) }
                            } }
                        }
                        SupervisionSection.MENU -> {
                            item { Text("${detail.menu.items.size} món · Giá niêm yết hiện tại", style = MaterialTheme.typography.titleSmall) }
                            if (detail.menu.items.isEmpty()) item { Text("Cửa hàng chưa cập nhật menu.") }
                            items(detail.menu.items, key = { "menu:${it.id}" }) { item -> MenuItemCard(item, { onMenu(item.id) }) }
                            item { StoreSectionTitle("Bảng giá và tài liệu tham chiếu") }
                            if (detail.menu.documents.isEmpty()) item { Text("Chưa có tài liệu tham chiếu.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            items(detail.menu.documents, key = { "document:${it.id}" }) { asset ->
                                StoreMenuRow(asset.name, "${asset.pageCount} trang · Xem tài liệu", FoodRescueIcon.RECEIPT, { assetId = asset.id })
                            }
                        }
                        SupervisionSection.BATCHES -> {
                            item { Text("${detail.catalog.batches.size} đợt bán", style = MaterialTheme.typography.titleSmall) }
                            if (detail.catalog.batches.isEmpty()) item { Text("Chưa tạo đợt bán.") }
                            items(detail.catalog.batches.sortedByDescending { it.salesStart }, key = { "batch:${it.id}" }) { batch ->
                                SupervisedBatchCard(batch, store.governance, !state.busy && state.error == null) { action -> onDecide(action, batch.id) }
                            }
                        }
                        SupervisionSection.BOXES -> {
                            item { Text("${detail.boxes.size} hộp · Mới đóng gói trước", style = MaterialTheme.typography.titleSmall) }
                            if (detail.boxes.isEmpty()) item { Text("Chưa có hộp được đóng gói.") }
                            items(detail.boxes, key = { "box:${it.id}" }) { box ->
                                StoreMenuRow(box.code, "${box.templateName}\n${box.conditionAt(detail.loadedAt).supervisionLabel} · ${money(box.value)}",
                                    FoodRescueIcon.BAG, { onBox(box.id) })
                            }
                        }
                        SupervisionSection.HISTORY -> {
                            item { Text("Lịch sử vận hành", style = MaterialTheme.typography.titleMedium)
                                Text("Menu hiển thị lần cập nhật gần nhất của từng món. Phiếu đóng gói và quyết định xử lý giữ nguyên lịch sử.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            if (detail.activity.isEmpty()) item { Text("Chưa có hoạt động được ghi nhận.") }
                            items(detail.activity, key = { it.id }) { event -> FoodRescueCard {
                                Text(reviewDate(event.atMillis), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(event.title, style = MaterialTheme.typography.titleSmall)
                                Text(event.detail, style = MaterialTheme.typography.bodyMedium)
                            } }
                        }
                    }
                    item { Text("Cập nhật ${reviewDate(detail.loadedAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
    detail?.menu?.documents?.find { it.id == assetId }?.let { MenuAssetPreview(it, { assetId = null }) }
}

@Composable
private fun SupervisedBatchCard(batch: SaleBatch, governance: StoreGovernance, enabled: Boolean,
    onDecide: (StoreRestrictionAction) -> Unit) {
    val restriction = governance.blockedBatches[batch.id]
    FoodRescueCard {
        Text(batch.template.name, style = MaterialTheme.typography.titleMedium)
        Text("Đợt ${batch.id.takeLast(8).uppercase()} · ${when (batch.control) {
            BatchControl.DRAFT -> "Bản nháp"; BatchControl.PUBLISHED -> "Đã mở bán"; BatchControl.PAUSED -> "Chủ cửa hàng tạm dừng"; BatchControl.ENDED -> "Đã kết thúc"
        }}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        StoreValue("Giá bán / Giá trị tối thiểu", "${money(batch.template.price)} / ${money(batch.template.minimumValue)}")
        StoreValue("Thời gian bán", "${reviewDate(batch.salesStart)} → ${reviewDate(batch.salesEnd)}")
        StoreValue("Thời gian nhận", "${reviewDate(batch.pickupStart)} → ${reviewDate(batch.pickupEnd)}")
        Text("${batch.quantity} hộp · ${batch.sold} đã bán · ${batch.held} đang giữ", style = MaterialTheme.typography.bodySmall)
        if (restriction != null) StoreErrorMessage("Đang ngừng bán: ${restriction.note}")
        else if (governance.suspension != null) Text("Đang bị chặn theo hạn chế toàn cửa hàng.", color = MaterialTheme.colorScheme.error)
        val action = if (restriction == null) StoreRestrictionAction.STOP_BATCH else StoreRestrictionAction.RELEASE_BATCH
        OutlinedButton({ onDecide(action) }, Modifier.fillMaxWidth(), enabled = enabled) { Text(action.label) }
    }
}

internal val BoxCondition.supervisionLabel get() = when (this) {
    BoxCondition.READY -> "Sẵn sàng bàn giao"
    BoxCondition.RECHECK_REQUIRED -> "Cần kiểm tra lại"
    BoxCondition.REJECTED -> "Không đạt"
    BoxCondition.EXPIRED -> "Hết hạn"
    BoxCondition.DELIVERED -> "Đã bàn giao"
}
