package com.foodrescue.app.feature.order.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.core.util.money
import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.surprisebox.domain.CatalogBox

@Composable
internal fun BookingScreen(
    state: BookingUiState,
    onBack: () -> Unit,
    onQuantity: (Int) -> Unit,
    onAccept: (Boolean) -> Unit,
    onNextPickup: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val box = state.box
    val draft = state.draft
    if (box == null || draft == null) {
        FoodRescuePage("Đặt hộp", onBack, modifier) {
            FoodRescueEmptyState("Không tìm thấy hộp", "Hãy chọn một hộp khác trong danh sách.", "Quay lại", onBack)
        }
        return
    }
    var policyOpen by rememberSaveable(draft.boxId) { mutableStateOf(false) }
    val elapsed = state.nowMillis >= draft.pickup.startMillis
    FoodRescuePage("Xác nhận đặt hộp", onBack, modifier, footer = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BookingAmount("Tổng tiền", state.total)
            FoodRescueButton("Xác nhận giữ hộp", onConfirm, Modifier.fillMaxWidth(), enabled = state.canConfirm)
        }
    }) {
        BookingBoxSummary(box)
        FoodRescueCard {
            Text("Số lượng hộp", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedIconButton(
                    onClick = { onQuantity(-1) }, enabled = draft.quantity > 1 && !elapsed,
                    modifier = Modifier.size(52.dp).semantics { contentDescription = "Giảm số lượng hộp" },
                    shape = RoundedCornerShape(14.dp),
                ) { Text("−", fontSize = 26.sp) }
                Text(draft.quantity.toString(), style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold, modifier = Modifier.semantics { contentDescription = "Số lượng: " + draft.quantity })
                OutlinedIconButton(
                    onClick = { onQuantity(1) }, enabled = draft.quantity < state.available && !elapsed,
                    modifier = Modifier.size(52.dp).semantics { contentDescription = "Tăng số lượng hộp" },
                    shape = RoundedCornerShape(14.dp),
                ) { Text("+", fontSize = 26.sp) }
            }
            Text("Còn " + state.available + " hộp cho khung giờ này", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            Text(money(box.price) + " × " + draft.quantity + " hộp", style = MaterialTheme.typography.bodyMedium)
            Text("Giá trị thực phẩm tối thiểu: " + money(bookingTotal(box.minimumValue, draft.quantity)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BookingPickupCard(box, draft.pickup)
        FoodRescueCard {
            Text("Thanh toán", style = MaterialTheme.typography.titleMedium)
            Text("QR VNPAY", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text("Thanh toán toàn bộ " + money(state.total) + " để xác nhận đơn hàng.", style = MaterialTheme.typography.bodyMedium)
        }
        if (elapsed) {
            Text("Khung giờ này đã bắt đầu. Chọn khung giờ mới trước khi đặt.", color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onNextPickup, modifier = Modifier.fillMaxWidth()) { Text("Chọn khung giờ mới") }
        } else if (state.available == 0) {
            Text("Hộp đã hết cho khung giờ này.", color = MaterialTheme.colorScheme.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            FoodRescueGlyph(FoodRescueIcon.CLOCK, tint = MaterialTheme.colorScheme.primary)
            Text("Giữ hộp tối đa 10 phút sau khi xác nhận và kết thúc trước giờ nhận.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = { policyOpen = true }, contentPadding = PaddingValues(0.dp)) {
            Text("Chính sách đặt hộp và dị ứng")
        }
        Row(Modifier.fillMaxWidth().toggleable(draft.accepted, role = Role.Checkbox, onValueChange = onAccept)
            .padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Checkbox(draft.accepted, onCheckedChange = null)
            Text("Tôi đồng ý với giờ nhận, thông tin dị ứng và chính sách đặt hộp.",
                Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        }
        state.issue?.let {
            Text(when (it) {
                BookingIssue.BOX_UNAVAILABLE -> "Hộp không còn khả dụng. Vui lòng chọn hộp khác."
                BookingIssue.PICKUP_STARTED -> "Khung giờ nhận đã bắt đầu. Vui lòng chọn lại."
                BookingIssue.SOLD_OUT -> "Hộp đã hết. Vui lòng chọn hộp khác."
                BookingIssue.QUANTITY -> "Số lượng còn lại đã thay đổi. Vui lòng giảm số hộp."
                BookingIssue.POLICY_REQUIRED -> "Vui lòng đồng ý với chính sách trước khi xác nhận."
            }, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
    if (policyOpen) BookingPolicySheet(box, onDismiss = { policyOpen = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookingPolicySheet(box: CatalogBox, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
            .navigationBarsPadding().padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Trước khi đặt hộp", style = MaterialTheme.typography.headlineSmall)
            Text("Nhận tại cửa hàng", style = MaterialTheme.typography.titleMedium)
            Text("Đến nhận đúng ngày và khung giờ hiển thị. Thành phần cụ thể của hộp được giữ bí mật đến khi nhận.",
                style = MaterialTheme.typography.bodyMedium)
            Text("Thông tin dị ứng", style = MaterialTheme.typography.titleMedium)
            Text(box.allergens, style = MaterialTheme.typography.bodyMedium)
            Text("Thời gian giữ hộp", style = MaterialTheme.typography.titleMedium)
            Text("Hộp được giữ tối đa 10 phút để hoàn tất thanh toán. Khi hết hạn hoặc đến giờ nhận, lượt giữ tự kết thúc.",
                style = MaterialTheme.typography.bodyMedium)
            Text("Hủy và hoàn tiền", style = MaterialTheme.typography.titleMedium)
            Text("Hủy hợp lệ trước giờ nhận được hoàn khoản đã thanh toán qua ứng dụng. Sau giờ nhận, yêu cầu cần được xem xét. Khoản hoàn có trạng thái xử lý riêng.",
                style = MaterialTheme.typography.bodyMedium)
            FoodRescueButton("Đã hiểu", onDismiss, Modifier.fillMaxWidth())
        }
    }
}
