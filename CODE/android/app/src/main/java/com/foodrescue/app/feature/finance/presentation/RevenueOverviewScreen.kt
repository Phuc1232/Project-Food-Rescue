package com.foodrescue.app.feature.finance.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.core.util.money
import com.foodrescue.app.feature.finance.domain.*
import com.foodrescue.app.feature.store.domain.PayoutBank
import com.foodrescue.app.feature.store.presentation.component.*

@Composable
internal fun RevenueOverviewScreen(storeName: String, state: RevenueUiState, banks: List<PayoutBank>,
    onPeriod: (RevenuePeriod) -> Unit, onMove: (Int) -> Unit, onCurrent: () -> Unit,
    onTransactions: (RevenueBasis) -> Unit, onSettlements: () -> Unit, onPayout: () -> Unit,
    onRetry: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Doanh thu", style = MaterialTheme.typography.headlineLarge)
                    Text(storeName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onRetry) { Text("Tải lại") }
            }
        }
        item { RevenuePeriodPicker(state, onPeriod, onMove, onCurrent) }
        val snapshot = state.snapshot
        val report = state.report
        when {
            state.loading -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            snapshot == null || report == null -> item { FoodRescueEmptyState("Chưa có số liệu", state.error ?: "Vui lòng tải lại.", "Thử lại", onRetry) }
            else -> {
                item { StoreHeroCard("CỬA HÀNG ĐƯỢC HƯỞNG", money(report.net)) {
                    Text("${report.completedOrders} đơn đủ điều kiện · theo ngày giao hộp", color = Color(0xFFD8E9DD), style = MaterialTheme.typography.bodyMedium)
                    Text("Hoa hồng hiện tại ${revenueRate(snapshot.commissionBps)}", color = Color(0xFFD8E9DD), style = MaterialTheme.typography.labelMedium)
                } }
                item { FoodRescueCard {
                    StoreSectionTitle("Chi tiết khoản được hưởng")
                    RevenueMoneyLine("Tiền các đơn đủ điều kiện", report.earnedGross)
                    RevenueMoneyLine("Hoa hồng FoodRescue", report.commission)
                    HorizontalDivider()
                    RevenueMoneyLine("Cửa hàng được hưởng", report.net, emphasized = true)
                    Text("Chưa bao gồm đơn chờ nhận, đang xử lý hủy hoặc khiếu nại.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton({ onTransactions(RevenueBasis.COLLECTION) }) { Text("Xem các đơn đã giao") }
                } }
                item { RevenueEarningsChart(snapshot, state.window) }
                item { FoodRescueCard {
                    StoreSectionTitle("Thanh toán & hoàn tiền")
                    RevenueMoneyLine("Khách đã thanh toán trong kỳ", report.received)
                    RevenueMoneyLine("Đã hoàn cho khách trong kỳ", report.refunded)
                    RevenueMoneyLine("Yêu cầu hoàn trong kỳ còn chờ xử lý", report.refundPending)
                    Text("Thanh toán tính theo ngày khách trả tiền; hoàn tiền tính theo ngày cập nhật kết quả.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton({ onTransactions(RevenueBasis.PAYMENT) }) { Text("Xem sổ giao dịch") }
                } }
                item { FoodRescueCard {
                    StoreSectionTitle("Đối soát & chi trả")
                    Text("Toàn bộ các kỳ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    RevenueMoneyLine("Đang chờ đối soát / chi trả", snapshot.awaitingPayout, emphasized = true)
                    RevenueMoneyLine("Đã chi trả", snapshot.paidOut)
                    TextButton(onSettlements) { Text("Xem các kỳ đối soát") }
                } }
                item {
                    snapshot.payout?.let { PayoutAccountCard(it, banks) }
                    StoreMenuRow(if (snapshot.payout == null) "Thiết lập tài khoản nhận tiền" else "Quản lý tài khoản nhận tiền",
                        if (snapshot.payout == null) "Bổ sung ngân hàng để sẵn sàng nhận chi trả" else "Áp dụng cho các khoản chi trả được xác lập sau khi thay đổi",
                        FoodRescueIcon.BANK, onPayout, Modifier.padding(top = if (snapshot.payout == null) 0.dp else 12.dp))
                }
                item { Text("Cập nhật ${revenueDate(snapshot.loadedAt, "HH:mm · dd/MM/yyyy")} · Giờ Việt Nam",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}
