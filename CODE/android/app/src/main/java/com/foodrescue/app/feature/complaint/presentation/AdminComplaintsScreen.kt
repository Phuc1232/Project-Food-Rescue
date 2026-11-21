package com.foodrescue.app.feature.complaint.presentation

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.core.util.money
import com.foodrescue.app.feature.complaint.domain.*
import com.foodrescue.app.feature.refund.presentation.component.label

@Composable
internal fun AdminComplaintsScreen(state: AdminComplaintsState, onSearch: (String) -> Unit,
    onFilter: (AdminComplaintFilter) -> Unit, onOpen: (String) -> Unit, onRefresh: () -> Unit,
    modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Trung tâm xử lý", style = MaterialTheme.typography.headlineMedium)
                Text("Khiếu nại khách hàng", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onRefresh, enabled = !state.busy) { FoodRescueGlyph(FoodRescueIcon.CLOCK, description = "Làm mới khiếu nại") }
        } }
        if (!state.loading && state.error == null) item {
            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF073F38), MaterialTheme.colorScheme.primary)),
                RoundedCornerShape(24.dp)).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("CẦN XỬ LÝ", style = MaterialTheme.typography.labelMedium, color = Color(0xFFCCE2D9))
                Text("${state.count(AdminComplaintFilter.OPEN)} khiếu nại", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Text("${state.count(AdminComplaintFilter.PENDING)} chờ tiếp nhận · ${state.count(AdminComplaintFilter.REVIEW)} đang xem xét",
                    style = MaterialTheme.typography.bodyMedium, color = Color(0xFFE2EEE8))
                val oldest = state.cases.filter { it.complaint.status.isOpen }.minByOrNull { it.complaint.createdAtMillis }
                if (oldest != null) Text("Gửi sớm nhất · ${complaintTimestamp(oldest.complaint.createdAtMillis)}",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFFE2EEE8))
                if (state.count(AdminComplaintFilter.REFUND) > 0) {
                    HorizontalDivider(color = Color.White.copy(alpha = .2f))
                    TextButton({ onFilter(AdminComplaintFilter.REFUND) }, contentPadding = PaddingValues(0.dp)) {
                        Text("${state.count(AdminComplaintFilter.REFUND)} đề nghị chờ hoàn tiền", Modifier.weight(1f), color = Color(0xFFE7F0C3))
                        FoodRescueGlyph(FoodRescueIcon.ARROW, tint = Color(0xFFE7F0C3))
                    }
                }
            }
        }
        item { FoodRescueTextField("Tìm khiếu nại", state.query, onSearch, placeholder = "Mã khiếu nại, đơn, cửa hàng",
            trailingIcon = { if (state.query.isNotBlank()) IconButton({ onSearch("") }) { FoodRescueGlyph(FoodRescueIcon.CLOSE, description = "Xóa tìm kiếm") }
                else FoodRescueGlyph(FoodRescueIcon.SEARCH) }) }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdminComplaintFilter.entries.forEach { filter -> FilterChip(state.filter == filter, { onFilter(filter) },
                label = { Text("${filter.label} · ${state.count(filter)}") }) }
        } }
        if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (state.error != null) item { ComplaintReviewError(state.error, onRefresh, !state.busy) }
        if (!state.loading && state.error == null && state.visible.isEmpty()) item {
            FoodRescueCard {
                FoodRescueGlyph(FoodRescueIcon.CHECK, tint = MaterialTheme.colorScheme.primary)
                Text(if (state.query.isBlank()) "Chưa có khiếu nại trong mục này" else "Không tìm thấy khiếu nại", style = MaterialTheme.typography.titleMedium)
                if (state.query.isNotBlank()) TextButton({ onSearch("") }) { Text("Xóa tìm kiếm") }
            }
        }
        if (state.visible.isNotEmpty()) item { Text("${state.visible.size} hồ sơ · ${if (state.filter == AdminComplaintFilter.CLOSED) "Cập nhật mới nhất" else "Gửi sớm nhất trước"}",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(state.visible, key = { it.key }) { item ->
            Surface({ onOpen(item.key) }, Modifier.fillMaxWidth(), enabled = !state.busy, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ComplaintReviewBadge(item.complaint)
                    Text(item.complaint.reason.label, style = MaterialTheme.typography.titleMedium)
                    Text(item.complaint.order.store, style = MaterialTheme.typography.bodyMedium)
                    Text("${item.complaint.id} · ${item.complaint.order.reference}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(item.complaint.description, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(complaintTimestamp(item.complaint.createdAtMillis), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        FoodRescueGlyph(FoodRescueIcon.ARROW, tint = MaterialTheme.colorScheme.primary)
                    }
                    item.complaint.refundRequest?.let { Text("Đề nghị hoàn ${money(it.amount)}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) }
                }
            }
        }
    }
}

@Composable
internal fun ComplaintReviewBadge(item: CustomerComplaint, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val foreground = when (item.status) { ComplaintStatus.PENDING -> Color(0xFF875B19); ComplaintStatus.REJECTED -> colors.error; else -> colors.primary }
    Surface(modifier, color = foreground.copy(alpha = .09f), shape = RoundedCornerShape(10.dp)) {
        Text(item.refundTransaction?.status?.label ?: if (item.refundRequest != null) "Chờ hoàn tiền" else item.status.label,
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = foreground, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun ComplaintReviewError(message: String, onRetry: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    FoodRescueCard(modifier) {
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        TextButton(onRetry, enabled = enabled) { Text("Tải lại") }
    }
}

@Composable
internal fun AdminComplaintOverviewCard(state: AdminComplaintsState, onOpen: () -> Unit, onRefresh: () -> Unit,
    modifier: Modifier = Modifier) {
    if (state.error != null) ComplaintReviewError(state.error, onRefresh, !state.busy, modifier)
    else FoodRescueCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FoodRescueGlyph(FoodRescueIcon.RECEIPT, tint = MaterialTheme.colorScheme.primary)
            Text("Khiếu nại khách hàng", style = MaterialTheme.typography.titleMedium)
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth()) else {
            Text("${state.count(AdminComplaintFilter.OPEN)} cần xử lý · ${state.count(AdminComplaintFilter.REFUND)} chờ hoàn tiền",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onOpen, Modifier.fillMaxWidth()) { Text("Mở trung tâm xử lý"); Spacer(Modifier.width(8.dp)); FoodRescueGlyph(FoodRescueIcon.ARROW) }
        }
    }
}
