package com.foodrescue.app.feature.finance.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.core.util.money
import com.foodrescue.app.feature.finance.domain.*
import com.foodrescue.app.feature.store.presentation.component.*

@Composable
internal fun AdminSettlementDetailScreen(state: AdminFinanceState, onBack: () -> Unit,
    onAction: (SettlementAction) -> Unit, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val item = state.selected ?: return
    val record = item.record
    FoodRescuePage("Chi tiết đối soát", onBack, modifier) {
        RevenueBadge(record.status.label, negative = record.status == SettlementStatus.FAILED)
        Text(item.storeName, style = MaterialTheme.typography.headlineMedium)
        Text(revenueRange(record.period), style = MaterialTheme.typography.titleSmall)
        if (record.status != SettlementStatus.PENDING) Text(record.id, style = MaterialTheme.typography.labelLarge)
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { FinanceError(it, onRefresh, !state.busy) }
        SettlementMoneyCard(record)
        FoodRescueCard {
            StoreSectionTitle("Tài khoản nhận tiền")
            StoreValue("Ngân hàng", record.destination?.bank ?: item.payout?.bank ?: "Chưa cung cấp")
            StoreValue("Chủ tài khoản", record.destination?.holder ?: item.payout?.holder ?: "Chưa cung cấp")
            StoreValue("Số tài khoản", record.destination?.accountNumber ?: if (record.destination != null)
                "•••• ${record.destination.lastFour}" else item.payout?.number ?: "Chưa cung cấp")
            Text(if (record.destination != null) "Thông tin được chốt cùng kỳ đối soát." else "Thông tin sẽ được chốt khi xác nhận đối soát.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (record.status != SettlementStatus.PAID && record.status != SettlementStatus.VOIDED) {
            item.issues.forEach { Text(it.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            item.actions.forEach { action ->
                if (action == SettlementAction.VOID || action == SettlementAction.FAIL) OutlinedButton({ onAction(action) },
                    Modifier.fillMaxWidth(), enabled = !state.busy && state.error == null) { Text(action.label) }
                else FoodRescueButton(action.label, { onAction(action) }, Modifier.fillMaxWidth(), enabled = !state.busy && state.error == null)
            }
        }
        record.transferReference?.let { StoreValue("Mã chuyển tiền", it) }
        record.paidAt?.let { StoreValue("Đã chi trả lúc", revenueDate(it, "HH:mm · dd/MM/yyyy")) }
        if (record.adjustments.isNotEmpty()) FoodRescueCard {
            StoreSectionTitle("Bù trừ hoàn tiền kỳ trước")
            record.adjustments.forEach { offset ->
                StoreValue(offset.sourceId, money(offset.amount))
                Text("Đơn ${offset.orderId}", style = MaterialTheme.typography.bodySmall)
            }
        }
        StoreSectionTitle("${record.lines.size} đơn được đối soát")
        record.lines.forEach { line -> FoodRescueCard {
            StoreValue(line.reference, money(line.net))
            Text("${line.quantity} hộp · ${line.boxName}", style = MaterialTheme.typography.bodyMedium)
            StoreValue("Hoa hồng ${revenueRate(line.commissionBps)}", money(line.commission))
            if (line.refunded > 0) StoreValue("Đã hoàn", money(line.refunded))
        } }
        FoodRescueCard {
            StoreSectionTitle("Lịch sử xử lý")
            if (record.history.isEmpty()) Text("Chưa có quyết định đối soát.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            record.history.asReversed().forEachIndexed { index, event ->
                if (index > 0) HorizontalDivider()
                Text(event.status.label, style = MaterialTheme.typography.titleSmall)
                Text(event.note, style = MaterialTheme.typography.bodyMedium)
                Text("${event.actor} · ${revenueDate(event.atMillis, "HH:mm · dd/MM/yyyy")}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun SettlementMoneyCard(record: RevenueSettlement, modifier: Modifier = Modifier) {
    FoodRescueCard(modifier) {
        RevenueMoneyLine("Tổng tiền đơn hàng", record.gross)
        RevenueMoneyLine("Đã hoàn trong các đơn", revenueSum(record.lines.map { it.refunded }))
        RevenueMoneyLine("Hoa hồng FoodRescue", record.commission)
        if (record.adjustment > 0) RevenueMoneyLine("Bù trừ hoàn tiền kỳ trước", record.adjustment)
        HorizontalDivider()
        RevenueMoneyLine("Cửa hàng được nhận", record.net, emphasized = true)
    }
}
