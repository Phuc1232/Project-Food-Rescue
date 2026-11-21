package com.foodrescue.app.feature.refund.presentation

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
import com.foodrescue.app.core.util.vietnamTimestamp
import com.foodrescue.app.feature.refund.domain.*
import com.foodrescue.app.feature.refund.presentation.component.*

@Composable
internal fun AdminRefundsScreen(state: AdminRefundsState, onSearch: (String) -> Unit,
    onFilter: (RefundFilter) -> Unit, onOpen: (String) -> Unit, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Tài chính", style = MaterialTheme.typography.headlineLarge)
                Text("Hoàn tiền · QR VNPAY", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onRefresh, enabled = !state.busy) { FoodRescueGlyph(FoodRescueIcon.CLOCK, description = "Làm mới hoàn tiền") }
        } }
        if (!state.loading && state.error == null) item {
            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF073F38), Color(0xFF226252))),
                RoundedCornerShape(24.dp)).padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("KHOẢN HOÀN CẦN XỬ LÝ", color = Color(0xFFD9E9C0), style = MaterialTheme.typography.labelMedium)
                Text(money(state.actionableAmount), style = MaterialTheme.typography.headlineLarge, color = Color.White)
                Text("${state.count(RefundFilter.ACTION)} yêu cầu đang chờ quyết định hoặc thử lại", color = Color(0xFFDFEDE7),
                    style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider(color = Color.White.copy(alpha = .18f))
                Text("${state.count(RefundFilter.PROCESSING)} đang xử lý  ·  ${state.count(RefundFilter.COMPLETED)} đã hoàn", color = Color.White,
                    style = MaterialTheme.typography.labelLarge)
            }
        }
        item { FoodRescueTextField("Tìm yêu cầu hoàn tiền", state.query, onSearch, placeholder = "Mã đơn, khiếu nại, cửa hàng",
            trailingIcon = { if (state.query.isBlank()) FoodRescueGlyph(FoodRescueIcon.SEARCH) else
                IconButton({ onSearch("") }) { FoodRescueGlyph(FoodRescueIcon.CLOSE, description = "Xóa tìm kiếm") } }) }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RefundFilter.entries.forEach { filter -> FilterChip(state.filter == filter, { onFilter(filter) },
                label = { Text("${filter.label} · ${state.count(filter)}") }) }
        } }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.error?.let { message -> item { RefundError(message, onRefresh, !state.busy) } }
        if (!state.loading && state.error == null && state.visible.isEmpty()) item {
            FoodRescueCard {
                FoodRescueGlyph(FoodRescueIcon.CHECK, tint = MaterialTheme.colorScheme.primary)
                Text(if (state.query.isBlank()) "Không có yêu cầu trong mục này" else "Không tìm thấy yêu cầu", style = MaterialTheme.typography.titleMedium)
                Text("Yêu cầu hoàn từ khiếu nại đã duyệt và đề nghị hủy của cửa hàng sẽ xuất hiện tại đây.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.query.isNotBlank()) TextButton({ onSearch("") }) { Text("Xóa tìm kiếm") }
            }
        }
        if (state.visible.isNotEmpty()) item { Text("${state.visible.size} yêu cầu · Ưu tiên giao dịch cần thử lại", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(state.visible, key = { it.key }) { row ->
            Surface({ onOpen(row.key) }, Modifier.fillMaxWidth(), enabled = !state.busy, shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RefundBadge(row.status)
                    Text(money(row.amount), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Text(row.source.label, style = MaterialTheme.typography.titleSmall)
                    Text(row.order?.boxSnapshot?.store ?: "Chưa có thông tin cửa hàng", style = MaterialTheme.typography.bodyMedium)
                    Text(row.order?.reference ?: row.orderId, style = MaterialTheme.typography.labelMedium)
                    Text(row.reason, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(vietnamTimestamp(row.requestedAtMillis), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        FoodRescueGlyph(FoodRescueIcon.ARROW, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
internal fun RefundError(message: String, onRefresh: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    FoodRescueCard(modifier) {
        Text(message, color = MaterialTheme.colorScheme.error)
        TextButton(onRefresh, enabled = enabled) { Text("Tải lại") }
    }
}
