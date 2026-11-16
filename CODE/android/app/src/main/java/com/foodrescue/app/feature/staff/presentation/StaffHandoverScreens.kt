package com.foodrescue.app.feature.staff.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.core.util.money
import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.order.presentation.component.HandoverHistory
import com.foodrescue.app.feature.order.presentation.component.handoverLabel
import com.foodrescue.app.feature.surprisebox.domain.PackedBox
import com.foodrescue.app.feature.surprisebox.presentation.component.boxDateTime

@Composable
internal fun StaffHandoverQueueScreen(state: StaffHandoverUiState, onSearch: (String) -> Unit,
    onFilter: (HandoverFilter) -> Unit, onOpen: (String) -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Bàn giao", style = MaterialTheme.typography.headlineLarge)
        HandoverNotice(state, onRetry)
        when {
            state.loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            !state.canRead -> FoodRescueCard {
                Text("Chưa có quyền xem đơn hàng", style = MaterialTheme.typography.titleMedium)
                Text("Liên hệ chủ cửa hàng để cập nhật phân công.")
            }
            else -> {
                val due = state.workspace.orders.count { it.isPaid && it.statusAt(state.now) == OrderStatus.WAITING_PICKUP &&
                    state.now >= it.pickup.startMillis && state.now < it.pickup.endMillis }
                HandoverHero("SẴN SÀNG ĐÓN KHÁCH", "$due đơn đến giờ nhận", "Xác minh mã · Chọn hộp · Bàn giao")
                FoodRescueTextField("Tìm đơn hàng", state.query, onSearch, placeholder = "Mã đơn hoặc tên hộp")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HandoverFilter.entries.forEach { filter -> FilterChip(state.filter == filter, { onFilter(filter) }, label = { Text(filter.label) }) }
                }
                Text("${state.visibleOrders.size} đơn · ${state.filter.label}", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.visibleOrders.isEmpty()) FoodRescueCard {
                    FoodRescueGlyph(FoodRescueIcon.CHECK, tint = MaterialTheme.colorScheme.primary)
                    Text("Chưa có đơn trong danh sách này", style = MaterialTheme.typography.titleMedium)
                    Text("Đơn đã thanh toán sẽ được sắp xếp theo lịch nhận tại cửa hàng.", style = MaterialTheme.typography.bodyMedium)
                }
                state.visibleOrders.forEach { order ->
                    Surface(onClick = { onOpen(order.id) }, shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(order.reference, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(order.boxSnapshot?.name ?: "Hộp bất ngờ", style = MaterialTheme.typography.titleMedium)
                            Text("${order.quantity} hộp · Đã thanh toán ${money(order.total)}", style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FoodRescueGlyph(FoodRescueIcon.CLOCK, Modifier.size(18.dp))
                                Text("${boxDateTime(order.pickup.startMillis)} – ${boxDateTime(order.pickup.endMillis)}", style = MaterialTheme.typography.bodySmall)
                            }
                            if (order.handoverFailures.isNotEmpty()) Text("${order.handoverFailures.size} ghi nhận sự cố", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun StaffHandoverDetailScreen(state: StaffHandoverUiState, onBack: () -> Unit, onCode: (String) -> Unit,
    onVerify: () -> Unit, onBox: (String) -> Unit, onConfirm: () -> Unit, onFailure: () -> Unit,
    onRetry: () -> Unit, modifier: Modifier = Modifier, onNoShow: ((String) -> Unit)? = null) {
    val order = state.order ?: return
    val completed = order.collectedAtMillis != null
    val availability = order.handoverAvailability(state.workspace.access, state.now)
    FoodRescuePage(if (completed) "Kết quả bàn giao" else "Bàn giao đơn hàng", onBack, modifier, footer = {
        if (completed || order.noShow != null) FoodRescueButton("Về danh sách bàn giao", onBack, Modifier.fillMaxWidth())
        else {
            Text("Đã chọn ${state.draft.boxIds.size}/${order.quantity} hộp", style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 8.dp))
            FoodRescueButton(if (state.busy) "Đang lưu…" else "Xác nhận bàn giao", onConfirm,
                Modifier.fillMaxWidth(), enabled = state.ready)
        }
    }) {
        if (completed) HandoverHero("HOÀN TẤT", "Đã bàn giao thành công", "${order.quantity} hộp · ${order.reference}")
        else {
            Text(order.boxSnapshot?.name ?: "Hộp bất ngờ", style = MaterialTheme.typography.headlineSmall)
            Text(order.reference, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        HandoverNotice(state, onRetry)
        FoodRescueCard {
            PackingValue("Số lượng cần giao", "${order.quantity} hộp")
            PackingValue("Lịch nhận", "${boxDateTime(order.pickup.startMillis)} – ${boxDateTime(order.pickup.endMillis)}")
            HorizontalDivider()
            Text("Đã thanh toán ${money(order.total)}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text("QR VNPAY · Thanh toán toàn bộ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!completed) {
            if (availability.isNotEmpty()) FoodRescueCard {
                Text("Chưa thể bàn giao", style = MaterialTheme.typography.titleMedium)
                availability.forEach { Text(it.handoverMessage, style = MaterialTheme.typography.bodyMedium) }
            } else {
                FoodRescueCard {
                    Text("01  Xác minh người nhận", style = MaterialTheme.typography.titleMedium)
                    FoodRescueTextField("Mã nhận hàng khách cung cấp", state.draft.code, onCode,
                        enabled = state.canWrite, placeholder = "Nhập hoặc dán mã nhận hàng",
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onVerify() }),
                        error = HandoverIssue.CODE.handoverMessage.takeIf { HandoverIssue.CODE in state.issues })
                    OutlinedButton(onVerify, Modifier.fillMaxWidth(), enabled = state.canWrite && state.draft.code.isNotBlank()) { Text("Xác minh mã") }
                    if (state.codeVerified) Text("Mã đã xác minh", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
                if (state.codeVerified) {
                    Text("02  Chọn ${order.quantity} hộp thực tế", style = MaterialTheme.typography.titleLarge)
                    Text("Ưu tiên hộp có hạn dùng gần nhất. Giá trị mỗi hộp từ ${money(order.boxSnapshot?.minimumValue ?: 0)}.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.availableBoxes.size < order.quantity) Text("Chưa đủ hộp đạt yêu cầu cho đơn này.", color = MaterialTheme.colorScheme.error)
                    state.availableBoxes.forEach { box -> HandoverBoxChoice(box, box.id in state.draft.boxIds,
                        state.canWrite && (box.id in state.draft.boxIds || state.draft.boxIds.size < order.quantity), { onBox(box.id) }) }
                }
                (state.issues - HandoverIssue.CODE).forEach { Text(it.handoverMessage, color = MaterialTheme.colorScheme.error) }
                TextButton(onFailure, Modifier.fillMaxWidth(), enabled = state.canWrite) { Text("Không thể bàn giao") }
            }
        } else {
            order.handover?.boxIds?.forEach { id ->
                state.workspace.boxes.firstOrNull { it.id == id }?.let { box -> FoodRescueCard {
                    Text(box.code, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    box.items.forEach { Text("${it.quantity} ${it.unit.staffLabel} · ${it.name}", style = MaterialTheme.typography.bodyMedium) }
                } }
            }
        }
        HandoverHistory(order)
        if (onNoShow != null && order.isPaid && state.now >= order.pickup.endMillis && order.cancelledAtMillis == null &&
            order.collectedAtMillis == null && order.ownerCancellation == null) {
            OutlinedButton({ onNoShow(order.id) }, Modifier.fillMaxWidth(), enabled = state.current && !state.busy) {
                Text(if (order.noShow != null) "Xem xác minh và hộp liên quan" else "Xác minh khách không đến")
            }
        }
        if (state.canWrite && !completed && order.noShow == null && HandoverIssue.ENDED in availability &&
            (availability - HandoverIssue.ENDED).isEmpty()) {
            TextButton(onFailure, Modifier.fillMaxWidth()) { Text("Không thể bàn giao") }
        }
    }
}

@Composable
internal fun StaffHandoverFailureScreen(state: StaffHandoverUiState, onBack: () -> Unit,
    onEdit: (HandoverFailureDraft) -> Unit, onSave: () -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    FoodRescuePage("Không thể bàn giao", onBack, modifier, footer = {
        FoodRescueButton(if (state.busy) "Đang lưu…" else "Ghi nhận sự cố", onSave, Modifier.fillMaxWidth(), enabled = state.canWrite)
    }) {
        Text("Ghi lại tình huống tại cửa hàng", style = MaterialTheme.typography.headlineSmall)
        Text(state.order?.reference.orEmpty(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text("Sự cố được chuyển cho chủ cửa hàng xử lý. Đơn chưa được ghi nhận đã giao hoặc No-show.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        HandoverNotice(state, onRetry)
        FoodRescueCard {
            Text("Lý do", style = MaterialTheme.typography.titleMedium)
            HandoverFailureReason.entries.forEach { reason ->
                Row(Modifier.fillMaxWidth().selectable(state.failure.reason == reason, enabled = state.canWrite,
                    role = Role.RadioButton, onClick = { onEdit(state.failure.copy(reason = reason)) }).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RadioButton(state.failure.reason == reason, onClick = null, enabled = state.canWrite)
                    Text(reason.handoverLabel, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        state.issues.forEach { Text(it.handoverMessage, color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(state.failure.note, { onEdit(state.failure.copy(note = it)) },
            Modifier.fillMaxWidth().semantics { contentDescription = "Ghi chú sự cố" },
            label = { Text("Ghi chú sự cố") }, minLines = 3, maxLines = 6, enabled = state.canWrite,
            supportingText = { Text("${state.failure.note.length}/500${if (state.failure.reason == HandoverFailureReason.OTHER) " · Tối thiểu 10 ký tự" else ""}") },
            isError = HandoverIssue.NOTE in state.issues, shape = RoundedCornerShape(16.dp))
    }
}

@Composable
private fun HandoverBoxChoice(box: PackedBox, selected: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().toggleable(selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { contentDescription = "Chọn hộp ${box.code}" }.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(box.code, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Checkbox(selected, onCheckedChange = null, enabled = enabled)
            }
            box.items.forEach { Text("${it.quantity} ${it.unit.staffLabel} · ${it.name}", style = MaterialTheme.typography.bodyMedium) }
            HorizontalDivider()
            Text("Giá trị ${money(box.value)}", style = MaterialTheme.typography.labelLarge)
            Text("Dùng trước ${boxDateTime(box.expiresAt)} · ${box.storage.staffLabel}", style = MaterialTheme.typography.bodySmall)
            Text("Dị ứng: ${box.allergens}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HandoverHero(eyebrow: String, title: String, subtitle: String) {
    Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFF133E32)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = Color(0xFFD8EDB4))
            Text(title, style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFDCE9DF))
        }
    }
}

@Composable
private fun HandoverNotice(state: StaffHandoverUiState, onRetry: () -> Unit) {
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onRetry, enabled = !state.busy) { Text("Tải lại dữ liệu") } }
    state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }
}

private val HandoverFilter.label get() = when (this) {
    HandoverFilter.WAITING -> "Đến giờ nhận"
    HandoverFilter.UPCOMING -> "Sắp đến giờ"
    HandoverFilter.OVERDUE -> "Quá giờ"
    HandoverFilter.NO_SHOW -> "No-show"
    HandoverFilter.ATTENTION -> "Cần kiểm tra"
    HandoverFilter.COMPLETED -> "Đã bàn giao"
}

private val HandoverIssue.handoverMessage get() = when (this) {
    HandoverIssue.ACCESS -> "Bạn chưa có quyền bàn giao đơn này."
    HandoverIssue.ORDER -> "Đơn không còn ở trạng thái cho phép bàn giao."
    HandoverIssue.PAYMENT -> "Đơn chưa được xác nhận thanh toán đầy đủ."
    HandoverIssue.EARLY -> "Chưa đến giờ nhận của đơn này."
    HandoverIssue.ENDED -> "Đã hết giờ nhận. Cần kiểm tra tình trạng đơn."
    HandoverIssue.CODE -> "Mã nhận hàng chưa khớp với đơn này."
    HandoverIssue.QUANTITY -> "Chọn đúng số lượng hộp cần giao."
    HandoverIssue.BOX -> "Có hộp không còn phù hợp. Tải lại để chọn hộp khác."
    HandoverIssue.REASON -> "Chọn lý do không thể bàn giao."
    HandoverIssue.NOTE -> "Ghi chú tối đa 500 ký tự; lý do khác cần ít nhất 10 ký tự và không có ký tự điều khiển."
    HandoverIssue.REQUEST -> "Phiên bàn giao không hợp lệ. Mở lại đơn để tiếp tục."
}
