package com.foodrescue.app.feature.refund.presentation.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.foodrescue.app.core.designsystem.component.FoodRescueCard
import com.foodrescue.app.core.util.money
import com.foodrescue.app.feature.refund.domain.*

internal val RefundStatus.label: String get() = when (this) {
    RefundStatus.PROCESSING -> "Đang hoàn tiền"
    RefundStatus.COMPLETED -> "Đã hoàn tiền"
    RefundStatus.FAILED -> "Hoàn tiền chưa thành công"
}

@Composable
internal fun RefundSummaryCard(refund: OrderRefund, onDetails: () -> Unit, modifier: Modifier = Modifier) {
    FoodRescueCard(modifier) {
        Text(refund.status.label, style = MaterialTheme.typography.titleMedium,
            color = if (refund.status == RefundStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        Text(money(refund.amount), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("QR VNPAY · Phương thức thanh toán ban đầu", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onDetails, modifier = Modifier.fillMaxWidth()) { Text("Chi tiết hoàn tiền") }
    }
}
