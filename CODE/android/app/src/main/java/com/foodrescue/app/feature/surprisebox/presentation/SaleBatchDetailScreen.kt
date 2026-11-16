package com.foodrescue.app.feature.surprisebox.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.core.util.money
import com.foodrescue.app.feature.store.presentation.component.*
import com.foodrescue.app.feature.surprisebox.domain.*
import com.foodrescue.app.feature.surprisebox.presentation.component.*

@Composable
internal fun SaleBatchDetailScreen(batch: SaleBatch, now: Long, busy: Boolean, error: String?, onBack: () -> Unit,
    onEdit: () -> Unit, onAction: (BatchAction) -> Unit, modifier: Modifier = Modifier) {
    val actions = batch.actionsAt(now)
    val primary = actions.firstOrNull { it != BatchAction.END }
    FoodRescuePage("Chi tiết đợt bán", onBack, modifier, footer = if (primary != null) ({
        FoodRescueButton(if (primary == BatchAction.PUBLISH && now < batch.salesStart) "Lên lịch mở bán" else primary.label,
            { onAction(primary) }, Modifier.fillMaxWidth(), enabled = !busy)
    }) else null) {
        BatchStatusBadge(batch.statusAt(now))
        StoreHeroCard(batch.template.category.label.uppercase(), batch.template.name) {
            Text("${money(batch.template.price)} / hộp", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Text("Giá trị tối thiểu ${money(batch.template.minimumValue)}", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD9EBE0))
        }
        StoreErrorMessage(error)
        FoodRescueCard {
            Text("Số lượng trong đợt", style = MaterialTheme.typography.titleMedium)
            BoxStock(batch)
            HorizontalDivider()
            StoreValue("Tổng mở bán", "${batch.quantity} hộp")
            HorizontalDivider()
            StoreValue("Đã đóng gói", "${batch.packed} hộp")
            StoreValue("Đủ điều kiện bàn giao", "${batch.eligible} hộp")
            StoreValue("Không đạt", "${batch.rejected} hộp")
            StoreValue("Hết hạn, chưa bàn giao", "${batch.expired} hộp")
            StoreValue("Đã bàn giao", "${batch.handedOver} hộp")
            StoreValue("Chờ kiểm tra lại", "${batch.recheck} hộp")
        }
        FoodRescueCard {
            Text("Lịch bán và nhận hàng", style = MaterialTheme.typography.titleMedium)
            StoreValue("Mở bán", boxDateTime(batch.salesStart))
            StoreValue("Đóng bán", boxDateTime(batch.salesEnd))
            HorizontalDivider()
            StoreValue("Bắt đầu nhận", boxDateTime(batch.pickupStart))
            StoreValue("Kết thúc nhận", boxDateTime(batch.pickupEnd))
        }
        FoodRescueCard {
            StoreValue("Thông tin hộp", batch.template.description)
            StoreValue("Dị ứng", batch.template.allergens)
        }
        if (batch.statusAt(now) != BatchStatus.ENDED) OutlinedButton(onEdit, Modifier.fillMaxWidth(), enabled = !busy) { Text("Chỉnh sửa đợt bán") }
        if (BatchAction.END in actions) TextButton({ onAction(BatchAction.END) }, Modifier.fillMaxWidth(), enabled = !busy) {
            Text("Kết thúc đợt bán", color = MaterialTheme.colorScheme.error)
        }
    }
}
