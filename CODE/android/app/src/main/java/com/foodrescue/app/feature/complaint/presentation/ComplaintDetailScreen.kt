package com.foodrescue.app.feature.complaint.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.core.util.money
import com.foodrescue.app.feature.complaint.domain.*
import com.foodrescue.app.feature.complaint.presentation.component.ComplaintEvidenceCard

@Composable
internal fun ComplaintDetailScreen(complaint: CustomerComplaint?, onBack: () -> Unit,
    onOrder: (String) -> Unit, modifier: Modifier = Modifier) {
    FoodRescuePage("Chi tiết khiếu nại", onBack, modifier, footer = {
        if (complaint != null) FoodRescueButton("Xem đơn hàng", { onOrder(complaint.order.id) }, Modifier.fillMaxWidth())
    }) {
        if (complaint == null) {
            FoodRescueEmptyState("Không tìm thấy khiếu nại", "Vui lòng kiểm tra lại danh sách của bạn.", "Quay lại", onBack)
        } else {
            FoodRescueCard {
                Text(complaint.status.label, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Text(complaint.id, style = MaterialTheme.typography.labelLarge)
                Text(when (complaint.status) {
                    ComplaintStatus.PENDING -> "Khiếu nại đã được gửi và đang chờ xem xét."
                    ComplaintStatus.IN_REVIEW -> "Nội dung và bằng chứng của bạn đang được xem xét."
                    ComplaintStatus.RESOLVED -> "Đã có kết quả xử lý cho khiếu nại của bạn."
                    ComplaintStatus.REJECTED -> "Xem phản hồi bên dưới để biết lý do."
                }, style = MaterialTheme.typography.bodyMedium)
            }
            FoodRescueCard {
                Text("Đơn hàng liên quan", style = MaterialTheme.typography.titleMedium)
                Text(complaint.order.store, style = MaterialTheme.typography.labelLarge)
                Text(complaint.order.reference, style = MaterialTheme.typography.bodyMedium)
                Text(complaint.order.boxName, style = MaterialTheme.typography.bodyMedium)
                Text("${complaint.order.quantity} hộp · ${money(complaint.order.amount)}", style = MaterialTheme.typography.bodyMedium)
            }
            FoodRescueCard {
                Text("Nội dung đã gửi", style = MaterialTheme.typography.titleMedium)
                Text(complaint.reason.label, style = MaterialTheme.typography.labelLarge)
                Text(complaint.description, style = MaterialTheme.typography.bodyMedium)
            }
            if (complaint.evidence.isNotEmpty()) {
                Text("Ảnh bằng chứng (${complaint.evidence.size})", style = MaterialTheme.typography.titleMedium)
                complaint.evidence.forEach { ComplaintEvidenceCard(it) }
            }
            FoodRescueCard {
                Text("Tiến trình xử lý", style = MaterialTheme.typography.titleMedium)
                Text("Đã gửi khiếu nại", style = MaterialTheme.typography.labelLarge)
                Text(complaintTimestamp(complaint.createdAtMillis), style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text(complaint.status.label, style = MaterialTheme.typography.labelLarge)
                if (complaint.status == ComplaintStatus.PENDING) Text("Chưa có phản hồi.", style = MaterialTheme.typography.bodyMedium)
                else Text(complaintTimestamp(complaint.updatedAtMillis), style = MaterialTheme.typography.bodySmall)
            }
            if (!complaint.response.isNullOrBlank()) FoodRescueCard {
                Text(if (complaint.status.isOpen) "Phản hồi" else "Kết quả xử lý", style = MaterialTheme.typography.titleMedium)
                Text(complaint.response, style = MaterialTheme.typography.bodyMedium)
                Text(complaintTimestamp(complaint.updatedAtMillis), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
