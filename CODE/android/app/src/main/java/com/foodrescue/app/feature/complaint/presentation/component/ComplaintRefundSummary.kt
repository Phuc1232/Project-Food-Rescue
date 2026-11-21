package com.foodrescue.app.feature.complaint.presentation.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.foodrescue.app.core.designsystem.component.FoodRescueCard
import com.foodrescue.app.core.util.money
import com.foodrescue.app.feature.complaint.domain.ComplaintRefundRequest

@Composable
internal fun ComplaintRefundSummary(request: ComplaintRefundRequest, modifier: Modifier = Modifier,
    transaction: com.foodrescue.app.feature.refund.domain.ManagedRefund? = null) {
    if (transaction != null) {
        com.foodrescue.app.feature.refund.presentation.component.ManagedRefundSummary(transaction, modifier)
        return
    }
    FoodRescueCard(modifier) {
        Text("Chờ xử lý hoàn tiền", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(money(request.amount), style = MaterialTheme.typography.headlineSmall)
        Text(request.id, style = MaterialTheme.typography.bodySmall)
        Text("Đề nghị đã được ghi nhận. Kết quả giao dịch hoàn tiền sẽ được cập nhật sau khi xử lý.", style = MaterialTheme.typography.bodyMedium)
    }
}
