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
import com.foodrescue.app.feature.store.domain.*
import com.foodrescue.app.feature.store.presentation.component.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RevenueSettlementsScreen(state: RevenueUiState, onBack: () -> Unit, onFilter: (SettlementFilter) -> Unit,
    onOpen: (String) -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(modifier, topBar = { TopAppBar(title = { Text("Đối soát & chi trả", style = MaterialTheme.typography.titleMedium) },
        navigationIcon = { IconButton(onBack) { FoodRescueGlyph(FoodRescueIcon.BACK, description = "Quay lại") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            val snapshot = state.snapshot
            item { Text("Toàn bộ các kỳ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (snapshot != null) item { StoreHeroCard("ĐANG CHỜ ĐỐI SOÁT / CHI TRẢ", money(snapshot.awaitingPayout)) {
                Text("Đã chi trả ${money(snapshot.paidOut)}", color = Color(0xFFD8E9DD), style = MaterialTheme.typography.bodyMedium)
            } }
            item { RevenueChips(SettlementFilter.entries.map { it.label }, state.settlementFilter.ordinal, { onFilter(SettlementFilter.entries[it]) }) }
            when {
                state.loading -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                snapshot == null -> item { FoodRescueEmptyState("Chưa tải được đối soát", state.error.orEmpty(), "Thử lại", onRetry) }
                state.visibleSettlements.isEmpty() -> item { StoreEmptyPanel(FoodRescueIcon.BANK, "Chưa có kỳ đối soát phù hợp",
                    "Các đơn đã giao và đủ điều kiện sẽ được tổng hợp tại đây.") }
                else -> items(state.visibleSettlements, key = { it.id }) { record ->
                    Surface({ onOpen(record.id) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            RevenueBadge(record.status.label, negative = record.status == SettlementStatus.FAILED)
                            Text(revenueRange(record.period), style = MaterialTheme.typography.titleMedium)
                            Text("${record.lines.size} đơn · ${if (record.status == SettlementStatus.PENDING) "Theo tuần giao hộp" else record.id}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(money(record.net), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                                FoodRescueGlyph(FoodRescueIcon.CHEVRON)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun RevenueSettlementScreen(record: RevenueSettlement, currentPayout: StorePayoutAccount?, banks: List<PayoutBank>,
    onBack: () -> Unit, onOrder: (String) -> Unit, onPayout: () -> Unit, modifier: Modifier = Modifier) {
    FoodRescuePage("Chi tiết đối soát", onBack, modifier) {
        RevenueBadge(record.status.label, negative = record.status == SettlementStatus.FAILED)
        Text(revenueRange(record.period), style = MaterialTheme.typography.headlineSmall)
        if (record.status != SettlementStatus.PENDING) Text(record.id, style = MaterialTheme.typography.labelLarge)
        FoodRescueCard {
            RevenueMoneyLine("Tiền các đơn trong kỳ", record.gross)
            RevenueMoneyLine("Hoa hồng FoodRescue", record.commission)
            HorizontalDivider()
            RevenueMoneyLine("Số tiền chi trả", record.net, emphasized = true)
        }
        Text(when (record.status) {
            SettlementStatus.PENDING -> "Các đơn được nhóm theo tuần giao hộp và đang chờ xác nhận đối soát. Chưa có lịch chuyển tiền."
            SettlementStatus.READY -> "Đối soát đã được xác nhận. Khoản chi trả đang chờ được chuyển đến tài khoản nhận tiền."
            SettlementStatus.PROCESSING -> "Lệnh chuyển tiền đang được xử lý. Trạng thái sẽ được cập nhật khi có kết quả."
            SettlementStatus.PAID -> "Khoản chi trả đã được xác nhận thành công."
            SettlementStatus.FAILED -> record.failureReason ?: "Chuyển tiền chưa thành công. Khoản này vẫn nằm trong số tiền chờ chi trả."
        }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (record.destination != null) {
            FoodRescueCard {
                StoreSectionTitle("Tài khoản của khoản chi trả")
                BankLogo(banks.findBank(record.destination.bank))
                StoreValue(record.destination.bank, "•••• ${record.destination.lastFour}")
                StoreValue("Chủ tài khoản", record.destination.holder)
                Text("Thông tin đã ghi nhận cho khoản chi trả này.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            currentPayout?.let { PayoutAccountCard(it, banks) }
            StoreMenuRow(if (currentPayout == null) "Bổ sung tài khoản nhận tiền" else "Kiểm tra tài khoản nhận tiền",
                "Tài khoản sẽ được ghi nhận khi xác lập khoản chi trả", FoodRescueIcon.BANK, onPayout)
        }
        if (record.updatedAt != null) FoodRescueCard {
            StoreValue("Cập nhật gần nhất", revenueDate(record.updatedAt, "HH:mm · dd/MM/yyyy"))
            record.paidAt?.let { StoreValue("Đã chi trả lúc", revenueDate(it, "HH:mm · dd/MM/yyyy")) }
            record.transferReference?.let { StoreValue("Mã chuyển tiền", it) }
        }
        StoreSectionTitle("${record.lines.size} đơn trong kỳ")
        record.lines.forEach { line ->
            Surface({ onOrder(line.orderId) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(line.reference, style = MaterialTheme.typography.titleSmall)
                    Text("${line.quantity} hộp · ${line.boxName}", style = MaterialTheme.typography.bodyMedium)
                    Text("Hoa hồng ${revenueRate(line.commissionBps)} · ${money(line.commission)}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(money(line.net), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
