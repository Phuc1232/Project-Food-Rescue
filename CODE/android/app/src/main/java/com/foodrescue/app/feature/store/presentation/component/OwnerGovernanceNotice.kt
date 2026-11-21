package com.foodrescue.app.feature.store.presentation.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.foodrescue.app.core.designsystem.component.FoodRescueCard
import com.foodrescue.app.feature.store.domain.StoreGovernance

@Composable
internal fun OwnerGovernanceNotice(governance: StoreGovernance, modifier: Modifier = Modifier) {
    val restrictions = listOfNotNull(governance.suspension) + governance.blockedBatches.values
    if (restrictions.isEmpty()) return
    FoodRescueCard(modifier) {
        StoreSectionTitle(if (governance.suspension != null) "Cửa hàng đang tạm ngừng bán" else "Có đợt bán bị hạn chế")
        restrictions.forEach { event ->
            event.batchName?.let { Text("${it} · Đợt ${event.batchId.orEmpty().takeLast(8).uppercase()}", style = MaterialTheme.typography.titleSmall) }
            Text(event.note, color = MaterialTheme.colorScheme.error)
            Text("FoodRescue · ${reviewDate(event.atMillis)}", style = MaterialTheme.typography.labelSmall)
        }
        Text("Hãy khắc phục theo yêu cầu. Đơn đã thanh toán vẫn cần được chuẩn bị và bàn giao; chỉ quản trị viên có thể gỡ hạn chế.",
            style = MaterialTheme.typography.bodySmall)
    }
}
