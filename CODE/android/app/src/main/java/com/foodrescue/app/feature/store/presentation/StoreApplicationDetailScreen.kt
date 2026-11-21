package com.foodrescue.app.feature.store.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.feature.store.domain.*
import com.foodrescue.app.feature.store.presentation.component.*

@Composable
internal fun StoreApplicationDetailScreen(application: StoreApplication, busy: Boolean, error: String?,
    onBack: () -> Unit, onDecide: (StoreReviewAction) -> Unit, onMap: () -> Unit, onRefresh: () -> Unit,
    modifier: Modifier = Modifier) {
    val store = application.store
    val profile = store.profile
    FoodRescuePage(application.reference, onBack, modifier, footer = if (store.status == StoreReviewStatus.PENDING) ({
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FoodRescueButton("Duyệt cửa hàng", { onDecide(StoreReviewAction.APPROVED) }, Modifier.fillMaxWidth(),
                enabled = !busy && error == null && profile.validationErrors().isEmpty())
            OutlinedButton({ onDecide(StoreReviewAction.CHANGES_REQUESTED) }, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !busy && error == null) {
                Text("Yêu cầu bổ sung")
            }
        }
    }) else null) {
        Text(profile.name, style = MaterialTheme.typography.headlineMedium)
        StoreStatusBadge(store.status)
        Text("Gửi lúc ${reviewDate(store.submittedAtMillis)}", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error != null) { StoreErrorMessage(error); TextButton(onRefresh, enabled = !busy) { Text("Tải lại hồ sơ") } }
        if (store.reviewNote.isNotBlank()) FoodRescueCard { StoreSectionTitle("Phản hồi xét duyệt"); Text(store.reviewNote) }
        FoodRescueCard {
            StoreSectionTitle("Thông tin kinh doanh")
            StoreValue("Loại hình", profile.category)
            if (profile.description.isNotBlank()) StoreValue("Giới thiệu", profile.description)
            StoreValue("Giờ hoạt động", "${profile.opensAt} – ${profile.closesAt}" + if (profile.closesAt < profile.opensAt) " (ngày hôm sau)" else "")
            HorizontalDivider()
            StoreValue("Người đại diện", profile.contactName)
            StoreValue("Số điện thoại", profile.phone)
            StoreValue("Email liên hệ", profile.contactEmail)
        }
        FoodRescueCard {
            StoreSectionTitle("Địa điểm cửa hàng")
            Text(profile.fullAddress, style = MaterialTheme.typography.bodyLarge)
            if (profile.location != null) {
                Text("Đã ghim vị trí cửa hàng", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onMap, Modifier.fillMaxWidth()) { FoodRescueGlyph(FoodRescueIcon.MAP); Spacer(Modifier.width(8.dp)); Text("Kiểm tra trên bản đồ") }
            } else Text("Chưa có vị trí trên bản đồ", color = MaterialTheme.colorScheme.error)
        }
        FoodRescueCard {
            StoreSectionTitle("Tài khoản nhận tiền")
            val payout = store.payout
            if (payout == null) Text("Chưa cập nhật. Chủ cửa hàng có thể bổ sung sau khi được duyệt.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            else { StoreValue("Ngân hàng", payout.bank); StoreValue("Chủ tài khoản", payout.holder); StoreValue("Số tài khoản", payout.maskedNumber) }
        }
        FoodRescueCard { StoreSectionTitle("Tài liệu đính kèm"); Text("Chưa có tài liệu đính kèm.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (store.status == StoreReviewStatus.PENDING && profile.validationErrors().isNotEmpty())
            StoreErrorMessage("Hồ sơ còn thiếu hoặc sai thông tin: ${profile.validationErrors().joinToString { it.label }}. Cần yêu cầu bổ sung trước khi duyệt.")
        FoodRescueCard { StoreReviewTimeline(store.reviewHistory) }
    }
}

@Composable
internal fun StoreApplicationDecisionScreen(state: StoreApplicationsState, onNote: (String) -> Unit,
    onBack: () -> Unit, onContinue: () -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit,
    modifier: Modifier = Modifier) {
    val application = state.selected ?: return
    val approving = state.decision == StoreReviewAction.APPROVED
    val title = if (approving) "Duyệt cửa hàng" else "Yêu cầu bổ sung"
    FoodRescuePage(title, onBack, modifier, footer = {
        FoodRescueButton("Xem lại quyết định", onContinue, Modifier.fillMaxWidth(), enabled = !state.busy)
    }) {
        Text(application.store.profile.name, style = MaterialTheme.typography.headlineSmall)
        Text(application.reference, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FoodRescueCard {
            StoreSectionTitle(if (approving) "Cho phép cửa hàng hoạt động" else "Gửi phản hồi cho chủ cửa hàng")
            Text(if (approving) "Cửa hàng được mở các chức năng vận hành. Vị trí đã ghim sẽ xuất hiện trên bản đồ khách hàng."
                else "Hồ sơ chuyển sang Cần bổ sung. Chủ cửa hàng sửa thông tin và gửi lại để xét duyệt.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(state.note, onNote, Modifier.fillMaxWidth(), enabled = !state.busy, minLines = 4, maxLines = 8,
            label = { Text(if (approving) "Ghi chú (không bắt buộc)" else "Thông tin cần bổ sung") },
            placeholder = { Text(if (approving) "Ghi nhận nội dung xét duyệt" else "Nêu rõ mục cần sửa và thông tin cần cung cấp") },
            isError = state.noteError != null, supportingText = { Text(state.noteError ?: "${state.note.length}/1.000") })
        StoreErrorMessage(state.error)
    }
    if (state.confirming) AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (approving) "Xác nhận duyệt cửa hàng?" else "Gửi yêu cầu bổ sung?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(application.store.profile.name, style = MaterialTheme.typography.titleMedium)
            Text(if (approving) "Kết quả sẽ được cập nhật cho chủ cửa hàng ngay sau khi lưu." else "Chủ cửa hàng sẽ nhận nội dung phản hồi sau đây:")
            if (state.note.isNotBlank()) Text(state.note, maxLines = 8, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        } },
        confirmButton = { TextButton(onConfirm, enabled = !state.busy) { Text(if (approving) "Xác nhận duyệt" else "Gửi yêu cầu") } },
        dismissButton = { TextButton(onDismiss, enabled = !state.busy) { Text("Xem lại") } })
}
