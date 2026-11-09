package com.foodrescue.app.feature.complaint.presentation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.feature.complaint.domain.*

@Composable
internal fun CustomerComplaintsScreen(state: ComplaintUiState, onBack: () -> Unit, onNew: () -> Unit,
    onCase: (String) -> Unit, onFilter: (ComplaintFilter) -> Unit, modifier: Modifier = Modifier) {
    FoodRescuePage("Khiếu nại của tôi", onBack, modifier, footer = {
        FoodRescueButton(if (state.draft.hasContent) "Tiếp tục bản nháp" else "Tạo khiếu nại", onNew, Modifier.fillMaxWidth())
    }) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ComplaintFilter.entries.forEach { filter -> FilterChip(state.filter == filter, { onFilter(filter) }, label = { Text(filter.label) }) }
        }
        if (state.draft.hasContent) Text("Bạn có một khiếu nại đang soạn.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary)
        if (state.visibleCases.isEmpty()) {
            FoodRescueEmptyState(if (state.cases.isEmpty()) "Chưa có khiếu nại" else "Chưa có khiếu nại trong mục này",
                "Các yêu cầu hỗ trợ và tiến trình xử lý của bạn sẽ xuất hiện tại đây.",
                if (state.cases.isEmpty()) "Tạo khiếu nại" else "Xem tất cả", if (state.cases.isEmpty()) onNew else { { onFilter(ComplaintFilter.ALL) } })
        }
        state.visibleCases.forEach { item ->
            FoodRescueCard {
                Text(item.status.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(item.order.store, style = MaterialTheme.typography.titleMedium)
                Text(item.reason.label, style = MaterialTheme.typography.bodyMedium)
                Text("Đơn ${item.order.reference}", style = MaterialTheme.typography.bodySmall)
                Text(item.id + "\n" + complaintTimestamp(item.createdAtMillis), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { onCase(item.id) }, modifier = Modifier.fillMaxWidth()) { Text("Xem chi tiết khiếu nại") }
            }
        }
    }
}
