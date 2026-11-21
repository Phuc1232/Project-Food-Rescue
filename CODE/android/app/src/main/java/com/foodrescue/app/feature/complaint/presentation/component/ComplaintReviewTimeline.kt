package com.foodrescue.app.feature.complaint.presentation.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.util.money
import com.foodrescue.app.feature.complaint.domain.*
import com.foodrescue.app.feature.complaint.presentation.complaintTimestamp

internal val ComplaintReviewAction.reviewLabel: String get() = when (this) {
    ComplaintReviewAction.RECEIVED -> "Tiếp nhận khiếu nại"
    ComplaintReviewAction.RESOLVED -> "Chấp nhận khiếu nại"
    ComplaintReviewAction.REJECTED -> "Từ chối khiếu nại"
    ComplaintReviewAction.REFUND_REQUESTED -> "Chấp nhận và đề nghị hoàn tiền"
}

@Composable
internal fun ComplaintReviewTimeline(complaint: CustomerComplaint, modifier: Modifier = Modifier, showActor: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Lịch sử xử lý", style = MaterialTheme.typography.titleMedium)
        Text("Khách hàng gửi khiếu nại", style = MaterialTheme.typography.labelLarge)
        Text(complaintTimestamp(complaint.createdAtMillis), style = MaterialTheme.typography.bodySmall)
        complaint.history.forEach { event ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(event.action.reviewLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(complaintTimestamp(event.atMillis), style = MaterialTheme.typography.bodySmall)
            if (showActor) Text(event.actor, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (event.note.isNotBlank()) Text(event.note, style = MaterialTheme.typography.bodyMedium)
            if (event.refundAmount > 0) Text("Đề nghị hoàn ${money(event.refundAmount)}", style = MaterialTheme.typography.labelLarge)
        }
        if (complaint.history.isEmpty() && complaint.status.isOpen)
            Text("Đang chờ phản hồi.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}
