package com.foodrescue.app.feature.complaint.presentation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.core.util.money
import com.foodrescue.app.feature.complaint.domain.*
import com.foodrescue.app.feature.complaint.presentation.component.*
import com.foodrescue.app.feature.order.domain.statusAt
import com.foodrescue.app.feature.order.presentation.component.label
import com.foodrescue.app.feature.order.presentation.component.HandoverHistory

@Composable
internal fun AdminComplaintDetailScreen(state: AdminComplaintsState, onBack: () -> Unit, onReceive: () -> Unit,
    onDecide: (ComplaintReviewAction) -> Unit, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val row = state.selected ?: return
    val complaint = row.complaint
    var section by rememberSaveable(row.key) { mutableIntStateOf(0) }
    FoodRescuePage("Chi tiết khiếu nại", onBack, modifier, footer = if (complaint.status.isOpen) ({
        FoodRescueButton(if (complaint.status == ComplaintStatus.PENDING) "Tiếp nhận khiếu nại" else "Ra quyết định",
            { if (complaint.status == ComplaintStatus.PENDING) onReceive() else onDecide(ComplaintReviewAction.RESOLVED) },
            Modifier.fillMaxWidth(), enabled = !state.busy && state.error == null)
    }) else null) {
        ComplaintReviewBadge(complaint)
        Text(complaint.reason.label, style = MaterialTheme.typography.headlineMedium)
        Text(complaint.id, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Gửi lúc ${complaintTimestamp(complaint.createdAtMillis)}", style = MaterialTheme.typography.bodySmall)
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.error != null) ComplaintReviewError(state.error, onRefresh, !state.busy)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Nội dung", "Đối chiếu", "Lịch sử").forEachIndexed { index, title ->
                FilterChip(section == index, { section = index }, label = { Text(title) })
            }
        }
        when (section) {
            0 -> {
                FoodRescueCard {
                    Text("Nội dung khách hàng gửi", style = MaterialTheme.typography.titleMedium)
                    Text(complaint.description, style = MaterialTheme.typography.bodyLarge)
                }
                FoodRescueCard {
                    Text("Đơn hàng liên quan", style = MaterialTheme.typography.titleMedium)
                    Text(complaint.order.store, style = MaterialTheme.typography.titleSmall)
                    Text(complaint.order.reference, style = MaterialTheme.typography.labelLarge)
                    Text("${complaint.order.boxName} · ${complaint.order.quantity} hộp")
                    Text("Giá trị đơn ${money(complaint.order.amount)}", style = MaterialTheme.typography.labelLarge)
                }
                Text("Bằng chứng (${complaint.evidence.size})", style = MaterialTheme.typography.titleMedium)
                if (complaint.evidence.isEmpty()) Text("Khách hàng chưa đính kèm ảnh.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                complaint.evidence.forEach { ComplaintEvidenceCard(it) }
                complaint.response?.let { FoodRescueCard {
                    Text("Phản hồi đã gửi", style = MaterialTheme.typography.titleMedium); Text(it)
                } }
                complaint.refundRequest?.let { ComplaintRefundSummary(it, transaction = complaint.refundTransaction) }
            }
            1 -> ComplaintOrderComparison(row, state.now)
            2 -> FoodRescueCard { ComplaintReviewTimeline(complaint, showActor = true) }
        }
    }
}

@Composable
private fun ComplaintOrderComparison(row: ComplaintReviewCase, now: Long) {
    val order = row.order
    if (order == null) {
        FoodRescueCard {
            Text("Chưa có dữ liệu đối chiếu", style = MaterialTheme.typography.titleMedium)
            Text("Không tìm thấy bản ghi đơn hàng. Chưa thể xác minh thanh toán để đề nghị hoàn tiền.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    FoodRescueCard {
        Text("Đơn hàng & thanh toán", style = MaterialTheme.typography.titleMedium)
        Text(order.statusAt(now).label, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        ComparisonValue("Mã đơn / giao dịch", order.reference)
        ComparisonValue("Tổng tiền", money(order.total))
        ComparisonValue("Thanh toán VNPAY", if (order.isPaid) "Đã thanh toán ${money(order.total)}" else "Chưa xác nhận thanh toán thành công")
        ComparisonValue("Khung giờ nhận", "${complaintTimestamp(order.pickup.startMillis)} – ${complaintTimestamp(order.pickup.endMillis)}")
        order.refund?.let { ComparisonValue("Khoản hoàn từ hủy đơn", money(it.amount)) }
        order.ownerCancellation?.let { Text("Cửa hàng đang yêu cầu xử lý hủy đơn.", color = MaterialTheme.colorScheme.error) }
        if (order.total != row.complaint.order.amount || order.reference != row.complaint.order.reference)
            Text("Thông tin đơn hiện tại khác thông tin trong khiếu nại. Cần đối chiếu kỹ trước khi ra quyết định.", color = MaterialTheme.colorScheme.error)
        HorizontalDivider()
        ComparisonValue("Còn có thể đề nghị hoàn", money(row.refundableAmount))
        if (row.refundableAmount == 0L && row.complaint.refundRequest == null) Text(refundUnavailableMessage(row),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (order.handover == null && order.handoverFailures.isEmpty() && order.noShow == null) {
        FoodRescueCard { Text("Chưa có nhật ký bàn giao.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    } else HandoverHistory(order)
    Text("Hộp thực tế liên quan", style = MaterialTheme.typography.titleMedium)
    if (row.packedBoxes.isEmpty()) Text("Chưa có bản ghi hộp thực tế gắn với đơn.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    row.packedBoxes.forEach { box -> FoodRescueCard {
        Text(box.code, style = MaterialTheme.typography.titleMedium)
        Text(box.templateName, style = MaterialTheme.typography.labelLarge)
        Text("Đóng gói: ${box.preparedByName} · ${complaintTimestamp(box.packedAt)}", style = MaterialTheme.typography.bodySmall)
        Text("Hạn dùng: ${complaintTimestamp(box.expiresAt)}", style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        box.items.forEach { item -> Text("${item.name} × ${item.quantity} · ${money(item.value)}", style = MaterialTheme.typography.bodyMedium) }
        ComparisonValue("Giá trị thực tế / tối thiểu", "${money(box.value)} / ${money(box.minimumValue)}")
        if (box.allergens.isNotBlank()) ComparisonValue("Dị ứng", box.allergens)
    } }
}

@Composable
private fun ComparisonValue(title: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
internal fun refundUnavailableMessage(row: ComplaintReviewCase): String = when {
    row.order == null -> "Chưa tìm thấy bản ghi đơn hàng để xác minh khoản thanh toán."
    !row.order.isPaid -> "Đơn chưa có thanh toán thành công."
    row.order.ownerCancellation != null -> "Yêu cầu hủy của cửa hàng cần được xử lý trước."
    row.order.collectedAtMillis == null && row.order.cancelledAtMillis == null && row.order.noShow == null ->
        "Đơn còn trong luồng nhận hàng. Cần hoàn tất nhận hàng hoặc hủy đơn trước khi đề nghị hoàn."
    else -> "Khoản thanh toán đã có yêu cầu hoàn tiền hoặc không còn số tiền có thể hoàn."
}
