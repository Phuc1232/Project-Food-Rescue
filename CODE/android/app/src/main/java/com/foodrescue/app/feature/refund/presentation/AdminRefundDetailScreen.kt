package com.foodrescue.app.feature.refund.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.core.util.money
import com.foodrescue.app.core.util.vietnamTimestamp
import com.foodrescue.app.feature.refund.domain.*
import com.foodrescue.app.feature.refund.presentation.component.*

@Composable
internal fun AdminRefundDetailScreen(state: AdminRefundsState, onBack: () -> Unit,
    onPrepare: (Boolean) -> Unit, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val row = state.selected ?: return
    val canReject = row.source == RefundSource.STORE_CANCELLATION && row.status == AdminRefundStatus.PENDING
    FoodRescuePage("Chi tiết hoàn tiền", onBack, modifier, footer = if (row.canProcess || canReject) ({
        if (row.canProcess) FoodRescueButton(if (row.status == AdminRefundStatus.FAILED) "Thử lại hoàn tiền" else
            if (row.source == RefundSource.STORE_CANCELLATION) "Duyệt hủy và hoàn tiền" else "Xử lý hoàn tiền",
            { onPrepare(true) }, Modifier.fillMaxWidth(), enabled = !state.busy && state.error == null)
        if (canReject) TextButton({ onPrepare(false) }, Modifier.fillMaxWidth(), enabled = !state.busy && state.error == null) { Text("Từ chối yêu cầu hủy") }
    }) else null) {
        RefundBadge(row.status)
        Text(money(row.amount), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        Text(row.source.label, style = MaterialTheme.typography.titleMedium)
        if (row.status == AdminRefundStatus.PROCESSING) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Đang chờ kết quả hoàn tiền. Trạng thái sẽ tự cập nhật.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.error?.let { RefundError(it, onRefresh, !state.busy) }
        if (!row.canProcess && row.status == AdminRefundStatus.PENDING) FoodRescueCard {
            Text("Chưa đủ điều kiện xử lý", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
            Text(if (row.order == null) "Chưa tìm thấy đơn hàng gốc để đối chiếu." else "Khoản đề nghị vượt số tiền còn có thể hoàn hoặc thanh toán chưa được xác nhận.")
        }
        FoodRescueCard {
            Text("Đối chiếu giao dịch", style = MaterialTheme.typography.titleMedium)
            Text("QR VNPAY · Phương thức thanh toán ban đầu", color = MaterialTheme.colorScheme.onSurfaceVariant)
            row.order?.let { order ->
                Text(order.reference, style = MaterialTheme.typography.titleSmall)
                Text(order.boxSnapshot?.store.orEmpty())
                RefundAmount("Khách đã thanh toán", if (order.isPaid) order.total else 0)
                RefundAmount("Đã hoàn thành công", row.completedAmount)
                RefundAmount("Khoản khác đã giữ để hoàn", (row.otherReserved - (row.completedAmount -
                    (row.transaction?.takeIf { it.status == AdminRefundStatus.COMPLETED }?.amount ?: 0))).coerceAtLeast(0))
                HorizontalDivider()
                if (row.status == AdminRefundStatus.PENDING || row.status == AdminRefundStatus.FAILED)
                    RefundAmount("Có thể hoàn cho yêu cầu này", row.availableAmount)
                RefundAmount("Số tiền của yêu cầu", row.amount)
            }
        }
        FoodRescueCard {
            Text("Nguồn yêu cầu", style = MaterialTheme.typography.titleMedium)
            if (row.source == RefundSource.COMPLAINT) Text(row.sourceId, style = MaterialTheme.typography.labelLarge)
            Text(row.reason)
            Text("Gửi lúc ${vietnamTimestamp(row.requestedAtMillis)}", style = MaterialTheme.typography.bodySmall)
            if (row.source == RefundSource.STORE_CANCELLATION && row.status == AdminRefundStatus.PENDING)
                Text("Chấp thuận sẽ hủy đơn, trả lại số lượng hộp và hoàn toàn bộ khoản khách đã thanh toán.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FoodRescueCard {
            Text("Lịch sử xử lý", style = MaterialTheme.typography.titleMedium)
            Text("Đã ghi nhận yêu cầu", style = MaterialTheme.typography.titleSmall)
            Text(vietnamTimestamp(row.requestedAtMillis), style = MaterialTheme.typography.bodySmall)
            row.transaction?.history.orEmpty().forEach { event ->
                HorizontalDivider()
                Text(event.status.label, style = MaterialTheme.typography.titleSmall)
                Text(event.note)
                Text("${event.actor} · ${vietnamTimestamp(event.atMillis)}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun RefundAmount(label: String, amount: Long, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(money(amount), style = MaterialTheme.typography.titleMedium)
    }
}
