package com.foodrescue.app.feature.finance.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.feature.finance.domain.*
import com.foodrescue.app.feature.store.presentation.component.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RevenueTransactionsScreen(state: RevenueUiState, onBack: () -> Unit, onPeriod: (RevenuePeriod) -> Unit,
    onMove: (Int) -> Unit, onCurrent: () -> Unit, onBasis: (RevenueBasis) -> Unit, onSearch: (String) -> Unit,
    onFilter: (RevenueFilter) -> Unit, onReset: () -> Unit, onOpen: (String) -> Unit, onRetry: () -> Unit,
    modifier: Modifier = Modifier) {
    Scaffold(modifier, topBar = { TopAppBar(title = { Text("Sổ giao dịch", style = MaterialTheme.typography.titleMedium) },
        navigationIcon = { IconButton(onBack) { FoodRescueGlyph(FoodRescueIcon.BACK, description = "Quay lại") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { RevenuePeriodPicker(state, onPeriod, onMove, onCurrent) }
            item { RevenueChips(RevenueBasis.entries.map { it.label }, state.basis.ordinal, { onBasis(RevenueBasis.entries[it]) }) }
            item { OutlinedTextField(state.query, onSearch, Modifier.fillMaxWidth().semantics { contentDescription = "Tìm giao dịch" },
                label = { Text("Mã đơn hoặc tên hộp") }, singleLine = true, leadingIcon = { FoodRescueGlyph(FoodRescueIcon.SEARCH) }) }
            item { RevenueChips(RevenueFilter.entries.map { it.label }, state.filter.ordinal, { onFilter(RevenueFilter.entries[it]) }) }
            when {
                state.loading -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                state.snapshot == null -> item { FoodRescueEmptyState("Chưa tải được giao dịch", state.error.orEmpty(), "Thử lại", onRetry) }
                state.visibleEntries.isEmpty() -> item { FoodRescueEmptyState("Không có giao dịch phù hợp", "Thử đổi kỳ hoặc bỏ bộ lọc để xem các đơn khác.", "Xóa bộ lọc", onReset) }
                else -> {
                    item { StoreSectionTitle("${state.visibleEntries.size} giao dịch · ${state.basis.label.lowercase()}") }
                    items(state.visibleEntries, key = { it.order.id }) { entry -> RevenueEntryCard(entry, { onOpen(entry.order.id) }) }
                }
            }
        }
    }
}

@Composable
internal fun RevenueTransactionScreen(entry: RevenueEntry, onBack: () -> Unit, onSettlement: (String) -> Unit, modifier: Modifier = Modifier) {
    FoodRescuePage("Chi tiết giao dịch", onBack, modifier) {
        Text(entry.order.reference, style = MaterialTheme.typography.headlineSmall)
        RevenueBadge(entry.status.label)
        Text(entry.order.boxSnapshot?.name ?: "Surprise Box", style = MaterialTheme.typography.titleMedium)
        Text("${entry.order.quantity} hộp", style = MaterialTheme.typography.bodyMedium)
        FoodRescueCard {
            RevenueMoneyLine("Khách đã thanh toán · VNPAY QR", entry.gross)
            RevenueMoneyLine("Hoa hồng ${revenueRate(entry.commissionBps)} được ghi nhận", entry.commission)
            if (entry.refunded > 0) RevenueMoneyLine("Đã hoàn cho khách", entry.refunded)
            if (entry.refundPending > 0) RevenueMoneyLine("Khoản hoàn đang chờ xử lý", entry.refundPending)
            HorizontalDivider()
            RevenueMoneyLine("Cửa hàng được hưởng", entry.net, emphasized = true)
        }
        Text(entry.explanation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FoodRescueCard {
            StoreValue("Thanh toán thành công", revenueDate(entry.paidAt, "HH:mm · dd/MM/yyyy"))
            entry.order.collectedAtMillis?.let { StoreValue("Đã giao hộp", revenueDate(it, "HH:mm · dd/MM/yyyy")) }
            entry.order.refund?.let { StoreValue("Cập nhật hoàn tiền", revenueDate(it.updatedAtMillis, "HH:mm · dd/MM/yyyy")) }
        }
        entry.settlementId?.let { id -> StoreMenuRow("Kỳ đối soát của đơn", "Xem số tiền, tài khoản nhận và trạng thái chi trả", FoodRescueIcon.BANK, { onSettlement(id) }) }
    }
}
