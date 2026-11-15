package com.foodrescue.app.feature.menu.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.core.util.money
import com.foodrescue.app.feature.menu.domain.*
import com.foodrescue.app.feature.menu.presentation.component.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun MenuDetailScreen(item: MenuItem, busy: Boolean, error: String?, onBack: () -> Unit, onEdit: () -> Unit,
    onStatus: () -> Unit, onPhoto: (MenuAsset) -> Unit, modifier: Modifier = Modifier) {
    FoodRescuePage("Chi tiết món", onBack, modifier, footer = { FoodRescueButton("Chỉnh sửa món", onEdit, Modifier.fillMaxWidth(), enabled = !busy) }) {
        MenuError(error)
        MenuPhoto(item.photo, Modifier.fillMaxWidth().aspectRatio(1.4f))
        if (item.photo != null) TextButton({ onPhoto(item.photo) }) { Text("Xem ảnh") }
        MenuStatusBadge(item.status)
        Text(item.name, style = MaterialTheme.typography.headlineMedium)
        Text("${item.category.label} · ${item.unit.label}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        FoodRescueCard {
            Text("GIÁ NIÊM YẾT", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(money(item.price), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
            Text("Cho 1 ${item.unit.label}", style = MaterialTheme.typography.bodyMedium)
        }
        if (item.description.isNotBlank()) {
            Text("Về món ăn", style = MaterialTheme.typography.titleMedium)
            Text(item.description, style = MaterialTheme.typography.bodyLarge)
        }
        Text("Cập nhật ${SimpleDateFormat("HH:mm · dd/MM/yyyy", Locale.forLanguageTag("vi-VN")).format(Date(item.updatedAtMillis))}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        Text("Trạng thái kinh doanh", style = MaterialTheme.typography.titleMedium)
        Text(if (item.status == MenuStatus.ACTIVE) "Ngừng kinh doanh khi cửa hàng không còn cung cấp món này. Thông tin món vẫn được lưu trong menu."
            else "Món đang ngừng kinh doanh. Bạn có thể mở lại khi cửa hàng sẵn sàng cung cấp.", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onStatus, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(if (item.status == MenuStatus.ACTIVE) "Ngừng kinh doanh món" else "Kinh doanh lại món")
        }
    }
}
