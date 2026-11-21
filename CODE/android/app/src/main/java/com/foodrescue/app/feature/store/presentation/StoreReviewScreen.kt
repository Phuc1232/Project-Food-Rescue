package com.foodrescue.app.feature.store.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.feature.store.domain.*
import com.foodrescue.app.feature.store.presentation.component.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun StoreReviewScreen(store: OwnerStore, email: String, onBack: () -> Unit, onAmend: () -> Unit, modifier: Modifier = Modifier) {
    val approved = store.status == StoreReviewStatus.APPROVED
    val changes = store.status == StoreReviewStatus.CHANGES_REQUIRED
    FoodRescuePage("Hồ sơ đối tác", onBack, modifier, footer = {
        FoodRescueButton(if (changes) "Bổ sung hồ sơ" else if (approved) "Về cửa hàng" else "Về tài khoản khách hàng",
            if (changes) onAmend else onBack, Modifier.fillMaxWidth())
    }) {
        StoreHeroCard("HỒ SƠ ĐỐI TÁC", if (approved) "Chào mừng đối tác!" else if (changes) "Hoàn thiện thêm hồ sơ" else "Hồ sơ đang được xét duyệt") {
            StoreStatusBadge(store.status)
        }
        Text(if (approved) "Cửa hàng của bạn đã được duyệt để hoạt động trên FoodRescue."
            else if (changes) "Cập nhật các thông tin được yêu cầu rồi gửi lại hồ sơ."
            else "FoodRescue đã tiếp nhận thông tin cửa hàng. Kết quả xét duyệt sẽ được cập nhật tại đây.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (store.reviewNote.isNotBlank()) FoodRescueCard { StoreSectionTitle("Phản hồi hồ sơ"); Text(store.reviewNote) }
        FoodRescueCard {
            StoreSectionTitle("Tiến trình hồ sơ")
            ReviewStep("Đã tiếp nhận", store.submittedAtMillis?.let {
                SimpleDateFormat("HH:mm · dd/MM/yyyy", Locale.forLanguageTag("vi-VN")).format(Date(it))
            } ?: "Thông tin cửa hàng đã được ghi nhận", true)
            ReviewStep("Xét duyệt thông tin", if (approved) "Đã hoàn tất" else if (changes) "Đang chờ bạn bổ sung" else "Đang kiểm tra hồ sơ", approved)
            ReviewStep("Kết quả xét duyệt", if (approved) "Cửa hàng đã được duyệt" else if (changes) "Cần bổ sung thông tin" else "Chưa có kết quả", approved)
        }
        FoodRescueCard { StoreSectionTitle(store.profile.name); StoreProfileValues(store.profile, email) }
        if (store.reviewHistory.isNotEmpty()) FoodRescueCard { StoreReviewTimeline(store.reviewHistory) }
    }
}

@Composable
private fun ReviewStep(title: String, detail: String, complete: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        FoodRescueGlyph(if (complete) FoodRescueIcon.CHECK else FoodRescueIcon.CLOCK,
            tint = if (complete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
