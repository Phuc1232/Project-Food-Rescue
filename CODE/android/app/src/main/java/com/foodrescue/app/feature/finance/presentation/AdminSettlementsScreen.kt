package com.foodrescue.app.feature.finance.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.core.util.money
import com.foodrescue.app.feature.finance.domain.*
import com.foodrescue.app.feature.store.presentation.component.*

@Composable
internal fun AdminSettlementsScreen(state: AdminFinanceState, onSearch: (String) -> Unit,
    onFilter: (SettlementStatus?) -> Unit, onOpen: (String) -> Unit, onRefresh: () -> Unit,
    modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Đối soát & chi trả", style = MaterialTheme.typography.headlineMedium)
                Text("Dòng tiền của đối tác", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onRefresh, enabled = !state.busy) { Text("Làm mới") }
        } }
        if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.error?.let { item { FinanceError(it, onRefresh, !state.busy) } }
        if (state.snapshot != null) {
            item { StoreHeroCard("CHỜ CHI TRẢ", money(state.ready)) {
                Text("Đã chi trả ${money(state.paid)}", color = Color(0xFFD8E9DD))
                Text("QR VNPAY · Thanh toán toàn bộ", color = Color(0xFFCEE5D6), style = MaterialTheme.typography.bodySmall)
            } }
            item { FoodRescueCard {
                StoreSectionTitle("Giao dịch toàn hệ thống")
                RevenueMoneyLine("Khách đã thanh toán", revenueSum(state.snapshot.entries.map { it.gross }))
                RevenueMoneyLine("Đã hoàn cho khách", revenueSum(state.snapshot.entries.map { it.refunded }))
                RevenueMoneyLine("Đang chờ hoàn", revenueSum(state.snapshot.entries.map { it.refundPending }))
                RevenueMoneyLine("Hoa hồng đủ điều kiện", revenueSum(state.snapshot.entries.map { it.commission }))
                Text("Đơn chưa giao, No-show và đơn đang khiếu nại được giữ ngoài kỳ chờ xác nhận.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
        }
        item { OutlinedTextField(state.query, onSearch, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Tìm cửa hàng, mã kỳ, mã chuyển tiền") }, shape = RoundedCornerShape(16.dp)) }
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(state.filter == null, { onFilter(null) }, label = { Text("Tất cả") })
            SettlementStatus.entries.forEach { status ->
                val count = state.snapshot?.settlements.orEmpty().count { it.record.status == status }
                FilterChip(state.filter == status, { onFilter(status) }, label = { Text("${status.label} · $count") })
            }
        } }
        if (!state.busy && state.snapshot != null && state.visible.isEmpty()) item {
            StoreEmptyPanel(FoodRescueIcon.BANK, "Chưa có kỳ đối soát phù hợp",
                "Đơn đã bàn giao và đủ điều kiện sẽ xuất hiện tại đây để kiểm tra và xác nhận.")
        }
        items(state.visible, key = { it.key }) { item ->
            Surface({ onOpen(item.key) }, Modifier.fillMaxWidth(), enabled = !state.busy,
                shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RevenueBadge(item.record.status.label, negative = item.record.status == SettlementStatus.FAILED)
                    Text(item.storeName, style = MaterialTheme.typography.titleMedium)
                    Text(revenueRange(item.record.period), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(money(item.record.net), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                            Text("${item.record.lines.size} đơn · CH-${kotlin.math.abs(item.record.storeId.toLong())}", style = MaterialTheme.typography.bodySmall)
                        }
                        FoodRescueGlyph(FoodRescueIcon.CHEVRON)
                    }
                    if (item.record.adjustment > 0) Text("Bù trừ kỳ trước ${money(item.record.adjustment)}", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
internal fun FinanceError(message: String, onRefresh: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    FoodRescueCard(modifier) {
        Text(message, color = MaterialTheme.colorScheme.error)
        TextButton(onRefresh, enabled = enabled) { Text("Tải lại") }
    }
}
