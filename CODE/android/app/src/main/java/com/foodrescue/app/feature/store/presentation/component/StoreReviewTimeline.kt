package com.foodrescue.app.feature.store.presentation.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.feature.store.domain.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun reviewDate(millis: Long?): String = millis?.let {
    SimpleDateFormat("HH:mm · dd/MM/yyyy", Locale.forLanguageTag("vi-VN")).format(Date(it))
} ?: "Chưa có thời gian gửi"

internal val StoreReviewAction.label: String get() = when (this) {
    StoreReviewAction.SUBMITTED -> "Đã gửi hồ sơ"
    StoreReviewAction.APPROVED -> "Đã duyệt cửa hàng"
    StoreReviewAction.CHANGES_REQUESTED -> "Yêu cầu bổ sung"
}

@Composable
internal fun StoreReviewTimeline(events: List<StoreReviewEvent>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        StoreSectionTitle("Lịch sử xét duyệt")
        if (events.isEmpty()) Text("Chưa có lịch sử xét duyệt được ghi nhận.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        events.asReversed().forEach { event ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FoodRescueGlyph(if (event.action == StoreReviewAction.APPROVED) FoodRescueIcon.CHECK else FoodRescueIcon.CLOCK,
                    tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(event.action.label, style = MaterialTheme.typography.titleSmall)
                    Text(reviewDate(event.atMillis), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(event.actor, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (event.note.isNotBlank()) Text(event.note, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
