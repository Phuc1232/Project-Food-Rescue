package com.foodrescue.app.feature.order.presentation.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.foodrescue.app.core.designsystem.component.FoodRescueCard
import com.foodrescue.app.feature.order.domain.BoxReservation
import com.foodrescue.app.feature.order.domain.HandoverFailureReason
import com.foodrescue.app.feature.order.presentation.bookingTimestamp

internal val HandoverFailureReason.handoverLabel: String get() = when (this) {
    HandoverFailureReason.CODE_MISMATCH -> "Mã nhận hàng không khớp"
    HandoverFailureReason.BOX_UNAVAILABLE -> "Không đủ hộp đạt yêu cầu"
    HandoverFailureReason.CUSTOMER_DECLINED -> "Khách từ chối nhận hộp"
    HandoverFailureReason.OTHER -> "Lý do khác"
}

@Composable
internal fun HandoverHistory(order: BoxReservation, modifier: Modifier = Modifier) {
    if (order.handover == null && order.handoverFailures.isEmpty() && order.noShow == null) return
    FoodRescueCard(modifier) {
        Text("Nhật ký bàn giao", style = MaterialTheme.typography.titleMedium)
        order.noShow?.let { receipt ->
            Text("Khách không đến nhận", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text("${receipt.employeeName} · ${bookingTimestamp(receipt.atMillis)}", style = MaterialTheme.typography.bodySmall)
            Text("${receipt.boxIds.size} hộp liên quan", style = MaterialTheme.typography.bodyMedium)
            if (receipt.note.isNotBlank()) Text(receipt.note, style = MaterialTheme.typography.bodyMedium)
        }
        order.handover?.let { receipt ->
            Text("Đã giao ${receipt.boxIds.size} hộp", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary)
            Text("${receipt.employeeName} · ${bookingTimestamp(receipt.atMillis)}", style = MaterialTheme.typography.bodySmall)
        }
        order.handoverFailures.asReversed().forEach { failure ->
            Text(failure.reason.handoverLabel, style = MaterialTheme.typography.titleSmall)
            if (failure.note.isNotBlank()) Text(failure.note, style = MaterialTheme.typography.bodyMedium)
            Text("${failure.employeeName} · ${bookingTimestamp(failure.atMillis)}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
