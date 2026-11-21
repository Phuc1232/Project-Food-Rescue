package com.foodrescue.app.feature.refund.presentation.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.FoodRescueCard
import com.foodrescue.app.core.util.money
import com.foodrescue.app.core.util.vietnamTimestamp
import com.foodrescue.app.feature.refund.domain.*

internal val AdminRefundStatus.label: String get() = when (this) {
    AdminRefundStatus.PENDING -> "Chờ xử lý"
    AdminRefundStatus.PROCESSING -> "Đang hoàn tiền"
    AdminRefundStatus.COMPLETED -> "Đã hoàn tiền"
    AdminRefundStatus.FAILED -> "Hoàn tiền thất bại"
    AdminRefundStatus.REJECTED -> "Đã từ chối hủy"
}
internal val RefundSource.label: String get() = when (this) {
    RefundSource.COMPLAINT -> "Khiếu nại đã duyệt"
    RefundSource.STORE_CANCELLATION -> "Cửa hàng yêu cầu hủy"
    RefundSource.NO_SHOW_POLICY -> "Chính sách khách không đến nhận"
}
@Composable
internal fun RefundBadge(status: AdminRefundStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        AdminRefundStatus.FAILED -> MaterialTheme.colorScheme.error
        AdminRefundStatus.PENDING -> Color(0xFF875B19)
        AdminRefundStatus.REJECTED -> MaterialTheme.colorScheme.onSurfaceVariant
        AdminRefundStatus.COMPLETED, AdminRefundStatus.PROCESSING -> MaterialTheme.colorScheme.primary
    }
    Surface(modifier, color = color.copy(alpha = .09f), shape = RoundedCornerShape(10.dp)) {
        Text(status.label, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = color, style = MaterialTheme.typography.labelMedium)
    }
}
@Composable
internal fun ManagedRefundSummary(refund: ManagedRefund, modifier: Modifier = Modifier) {
    FoodRescueCard(modifier) {
        RefundBadge(refund.status)
        if (refund.status != AdminRefundStatus.REJECTED) Text(money(refund.amount), style = MaterialTheme.typography.headlineSmall)
        Text(refund.source.label, style = MaterialTheme.typography.titleSmall)
        Text(when (refund.status) {
            AdminRefundStatus.PENDING -> "Yêu cầu đang chờ xử lý."
            AdminRefundStatus.PROCESSING -> "Đang hoàn về phương thức QR VNPAY đã thanh toán."
            AdminRefundStatus.COMPLETED -> "Khoản hoàn đã được ghi nhận về phương thức thanh toán ban đầu."
            AdminRefundStatus.FAILED -> "Khoản hoàn chưa hoàn tất. Bộ phận hỗ trợ sẽ tiếp tục xử lý yêu cầu này."
            AdminRefundStatus.REJECTED -> "Yêu cầu hủy của cửa hàng không được chấp thuận. Đơn giữ nguyên trạng thái."
        })
        refund.history.lastOrNull { it.actor != "Hệ thống" }?.let { Text(it.note, style = MaterialTheme.typography.bodyMedium) }
        Text("Cập nhật ${vietnamTimestamp(refund.updatedAtMillis)}", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
