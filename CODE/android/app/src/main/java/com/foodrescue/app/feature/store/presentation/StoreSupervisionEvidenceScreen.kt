package com.foodrescue.app.feature.store.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.core.util.money
import com.foodrescue.app.feature.menu.domain.MenuItem
import com.foodrescue.app.feature.menu.presentation.component.*
import com.foodrescue.app.feature.store.domain.StoreSupervisionDetail
import com.foodrescue.app.feature.store.presentation.component.*
import com.foodrescue.app.feature.surprisebox.domain.*

@Composable
internal fun SupervisedMenuItemScreen(item: MenuItem, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var preview by rememberSaveable(item.id) { mutableStateOf(false) }
    FoodRescuePage("Đối chiếu menu", onBack, modifier) {
        Text(item.name, style = MaterialTheme.typography.headlineMedium)
        MenuStatusBadge(item.status)
        item.photo?.let { photo ->
            MenuPhoto(photo, Modifier.fillMaxWidth().height(210.dp))
            TextButton({ preview = true }) { Text("Xem ảnh món") }
        }
        FoodRescueCard {
            Text(money(item.price), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
            StoreValue("Đơn vị", item.unit.label)
            StoreValue("Phân loại", item.category.label)
            StoreValue("Mô tả", item.description)
            StoreValue("Cập nhật gần nhất", reviewDate(item.updatedAtMillis))
        }
    }
    if (preview) item.photo?.let { MenuAssetPreview(it, { preview = false }) }
}

@Composable
internal fun SupervisedPackedBoxScreen(box: PackedBox, detail: StoreSupervisionDetail, onBack: () -> Unit,
    modifier: Modifier = Modifier) {
    FoodRescuePage(box.code, onBack, modifier) {
        Text(box.templateName, style = MaterialTheme.typography.headlineSmall)
        Text(box.conditionAt(detail.loadedAt).supervisionLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        FoodRescueCard {
            StoreSectionTitle("Giá trị khi đóng gói")
            Text(money(box.value), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
            StoreValue("Cam kết tối thiểu", money(box.minimumValue))
            Text(if (box.value >= box.minimumValue) "Đủ giá trị cam kết · Chênh lệch +${money(box.value - box.minimumValue)}"
                else "Thiếu ${money(box.minimumValue - box.value)} so với cam kết", color = if (box.value >= box.minimumValue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        }
        Text("Các món thực tế", style = MaterialTheme.typography.titleMedium)
        box.items.forEach { item -> FoodRescueCard {
            Text(item.name, style = MaterialTheme.typography.titleSmall)
            StoreValue("Số lượng", "${item.quantity} ${item.unit.label}")
            StoreValue("Đơn giá lúc đóng gói", money(item.unitPrice))
            StoreValue("Giá trị ghi nhận", money(item.value))
            val current = detail.menu.items.find { it.id == item.itemId }
            if (current == null) Text("Món không còn trong menu hiện tại.", style = MaterialTheme.typography.bodySmall)
            else if (current.price != item.unitPrice || current.unit != item.unit)
                Text("Menu hiện tại: ${money(current.price)} / ${current.unit.label}. Giá trị phiếu đóng gói giữ nguyên.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        FoodRescueCard {
            StoreSectionTitle("Truy xuất hộp")
            StoreValue("Người đóng gói", box.preparedByName)
            StoreValue("Thời điểm đóng gói", reviewDate(box.packedAt))
            StoreValue("Hạn sử dụng", reviewDate(box.expiresAt))
            StoreValue("Bảo quản", when (box.storage) { BoxStorage.AMBIENT -> "Nhiệt độ thường"; BoxStorage.CHILLED -> "Bảo quản mát"; BoxStorage.FROZEN -> "Đông lạnh" })
            StoreValue("Thông tin dị ứng", box.allergens)
            box.handover?.let { StoreValue("Đã bàn giao cho đơn", it.reference); StoreValue("Thời điểm bàn giao", reviewDate(it.atMillis)) }
            box.noShow?.let { StoreValue("Đơn khách không đến nhận", it.reference) }
        }
        if (box.inspections.isNotEmpty()) FoodRescueCard {
            StoreSectionTitle("Lịch sử kiểm tra")
            box.inspections.reversed().forEach { inspection ->
                Text(if (inspection.result == InspectionResult.PASSED) "Kiểm tra đạt" else "Không đạt", style = MaterialTheme.typography.titleSmall)
                Text("${reviewDate(inspection.atMillis)} · ${inspection.employeeName}", style = MaterialTheme.typography.bodySmall)
                if (inspection.note.isNotBlank()) Text(inspection.note)
                HorizontalDivider()
            }
        }
    }
}
