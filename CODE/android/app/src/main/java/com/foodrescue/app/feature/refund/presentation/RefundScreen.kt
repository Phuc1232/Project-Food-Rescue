package com.foodrescue.app.feature.refund.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.core.util.money
import com.foodrescue.app.feature.refund.domain.*
import com.foodrescue.app.feature.refund.presentation.component.label

internal data class RefundUiState(val refund: OrderRefund, val orderReference: String, val store: String,
    val quantity: Int, val paidAmount: Long, val requestedAt: String, val updatedAt: String, val reason: String)

@Composable
internal fun RefundScreen(state: RefundUiState?, onBack: () -> Unit, onRetry: () -> Unit,
    onOrder: () -> Unit, onSupport: () -> Unit, modifier: Modifier = Modifier) {
    if (state == null) {
        FoodRescuePage("Hoàn tiền", onBack, modifier) {
            FoodRescueEmptyState("Chưa có khoản hoàn", "Thông tin hoàn tiền sẽ xuất hiện sau khi hủy đơn hợp lệ.", "Về đơn hàng", onOrder)
        }
        return
    }
    val status = state.refund.status
    val colors = MaterialTheme.colorScheme
    FoodRescuePage("Hủy đơn và hoàn tiền", onBack, modifier, footer = {
        FoodRescueButton(if (status == RefundStatus.FAILED) "Thử hoàn tiền lại" else "Xem đơn hàng",
            if (status == RefundStatus.FAILED) onRetry else onOrder, Modifier.fillMaxWidth())
    }) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(shape = CircleShape, color = colors.primaryContainer) {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    if (status == RefundStatus.PROCESSING) CircularProgressIndicator(Modifier.size(28.dp))
                    else FoodRescueGlyph(if (status == RefundStatus.COMPLETED) FoodRescueIcon.CHECK else FoodRescueIcon.CLOSE,
                        tint = if (status == RefundStatus.FAILED) colors.error else colors.primary)
                }
            }
            Text("Đơn hàng đã hủy", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(when (status) {
                RefundStatus.PROCESSING -> "Khoản hoàn đang được xử lý. Bạn có thể theo dõi trong chi tiết đơn."
                RefundStatus.COMPLETED -> "Khoản hoàn đã hoàn tất qua phương thức thanh toán ban đầu."
                RefundStatus.FAILED -> "Đơn vẫn được giữ ở trạng thái đã hủy. Kiểm tra kết nối và thử hoàn tiền lại."
            }, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = colors.onSurfaceVariant)
        }
        FoodRescueCard {
            Text(status.label, style = MaterialTheme.typography.titleMedium,
                color = if (status == RefundStatus.FAILED) colors.error else colors.primary)
            Text(money(state.refund.amount), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = colors.primary)
            Text("QR VNPAY", style = MaterialTheme.typography.labelLarge)
            Text("Yêu cầu lúc " + state.requestedAt, style = MaterialTheme.typography.bodySmall)
            if (status != RefundStatus.PROCESSING) Text("Cập nhật lúc " + state.updatedAt, style = MaterialTheme.typography.bodySmall)
        }
        FoodRescueCard {
            Text("Thông tin đơn đã hủy", style = MaterialTheme.typography.titleMedium)
            Text(state.store, style = MaterialTheme.typography.titleSmall)
            Text("${state.quantity} hộp · " + money(state.paidAmount))
            Text("Mã thanh toán: " + state.orderReference, style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("Lý do: " + state.reason, style = MaterialTheme.typography.bodyMedium)
        }
        TextButton(onClick = onSupport, modifier = Modifier.fillMaxWidth()) { Text("Gửi khiếu nại về đơn này") }
    }
}
