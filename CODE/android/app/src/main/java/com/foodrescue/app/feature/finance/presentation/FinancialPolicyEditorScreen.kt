package com.foodrescue.app.feature.finance.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.core.util.money
import com.foodrescue.app.feature.finance.domain.*
import com.foodrescue.app.feature.store.presentation.component.*
import kotlinx.coroutines.flow.collectLatest

@Composable
internal fun FinancialPolicyEditorScreen(state: FinancialPolicyState, onBack: () -> Unit,
    onChange: (PolicyDraft) -> Unit, onReview: () -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit,
    modifier: Modifier = Modifier) {
    val draft = state.draft ?: return
    FoodRescuePage("Điều chỉnh chính sách", onBack, modifier, footer = {
        FoodRescueButton("Xem lại chính sách", onReview, Modifier.fillMaxWidth(), enabled = !state.busy)
    }) {
        Text("Phiên bản ${(state.book?.revision ?: 0) + 1}", style = MaterialTheme.typography.headlineSmall)
        Text("Áp dụng cho đơn được tạo từ thời điểm hiệu lực. Lịch sử đơn cũ được giữ nguyên.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        StoreSectionTitle("Hoa hồng")
        PolicyNumberField("Tỷ lệ hoa hồng (%)", draft.commission, "0–100%, tối đa 2 chữ số thập phân", !state.busy) {
            onChange(draft.copy(commission = it)) }
        StoreSectionTitle("Hủy & hoàn tiền")
        PolicyNumberField("Hủy trước giờ nhận (phút)", draft.cancellationMinutes, "0–1440 phút; 0 là trước lúc bắt đầu nhận", !state.busy) {
            onChange(draft.copy(cancellationMinutes = it)) }
        PolicyNumberField("Hoàn khi hủy hợp lệ (%)", draft.cancellationRefund, "0–100% khoản đã thanh toán", !state.busy) {
            onChange(draft.copy(cancellationRefund = it)) }
        StoreSectionTitle("Khách không đến nhận")
        PolicyNumberField("Thời gian chờ thêm (phút)", draft.noShowGrace, "0–120 phút sau khi kết thúc giờ nhận", !state.busy) {
            onChange(draft.copy(noShowGrace = it)) }
        PolicyNumberField("Hoàn khi xác minh No-show (%)", draft.noShowRefund, "0–100%; 0 là không hoàn theo chính sách", !state.busy) {
            onChange(draft.copy(noShowRefund = it)) }
        StoreSectionTitle("Thời điểm hiệu lực")
        Row {
            FilterChip(!draft.scheduled, { onChange(draft.copy(scheduled = false)) }, enabled = !state.busy, label = { Text("Ngay khi xác nhận") })
        }
        FilterChip(draft.scheduled, { onChange(draft.copy(scheduled = true)) }, enabled = !state.busy, label = { Text("Hẹn thời điểm") })
        if (draft.scheduled) PolicyTextField(draft.effectiveText, { onChange(draft.copy(effectiveText = it.take(16))) },
            "Ngày và giờ hiệu lực", "dd/MM/yyyy HH:mm · Giờ Việt Nam", enabled = !state.busy,
            isError = state.attempted && draft.effectiveAt(state.now) == null)
        PolicyTextField(draft.reason, { onChange(draft.copy(reason = it.take(500))) },
            "Lý do điều chỉnh", "${draft.reason.length}/500 · Tối thiểu 10 ký tự",
            enabled = !state.busy, minLines = 3, singleLine = false,
            isError = state.attempted && !validFinanceReason(draft.reason))
        draft.terms()?.let { terms -> FoodRescueCard {
            StoreSectionTitle("Minh họa trên đơn 100.000 đ")
            RevenueMoneyLine("Hoa hồng khi giao thành công", revenueCommission(100_000, terms.commissionBps))
            RevenueMoneyLine("Cửa hàng được hưởng", 100_000 - revenueCommission(100_000, terms.commissionBps))
            RevenueMoneyLine("Khách nhận khi hủy hợp lệ", revenueCommission(100_000, terms.cancellationRefundBps))
        } }
        if (state.attempted && !state.valid) Text("Kiểm tra các giá trị, lý do và thời điểm hiệu lực. Chính sách mới cần có thay đổi.", color = MaterialTheme.colorScheme.error)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    if (state.confirming) AlertDialog(onDismissRequest = onDismiss, title = { Text("Công bố chính sách mới?") },
        text = { Text("Hoa hồng: ${draft.commission}%\nHạn hủy: trước giờ nhận ${draft.cancellationMinutes} phút\nHoàn khi hủy: ${draft.cancellationRefund}%\n" +
            "No-show: chờ ${draft.noShowGrace} phút, hoàn ${draft.noShowRefund}%\n" +
            if (draft.scheduled) "Hiệu lực: ${draft.effectiveText}" else "Hiệu lực ngay khi xác nhận") },
        confirmButton = { TextButton(onConfirm, enabled = !state.busy) { Text("Công bố") } },
        dismissButton = { TextButton(onDismiss, enabled = !state.busy) { Text("Kiểm tra lại") } })
}

@Composable
private fun PolicyNumberField(label: String, value: String, hint: String, enabled: Boolean, onChange: (String) -> Unit) {
    PolicyTextField(value, { onChange(it.take(8)) }, label, hint, enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
}

@Composable
private fun PolicyTextField(value: String, onChange: (String) -> Unit, label: String, hint: String,
    enabled: Boolean, modifier: Modifier = Modifier, keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true, minLines: Int = 1, isError: Boolean = false) {
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    LaunchedEffect(focused, ime, density) {
        if (focused) snapshotFlow { ime.getBottom(density) }.collectLatest { height ->
            if (height > 0) { withFrameNanos { }; requester.bringIntoView() }
        }
    }
    OutlinedTextField(value, onChange, modifier.fillMaxWidth().bringIntoViewRequester(requester)
        .onFocusChanged { focused = it.isFocused }, enabled = enabled, label = { Text(label) },
        supportingText = { Text(hint) }, singleLine = singleLine, minLines = minLines,
        isError = isError, keyboardOptions = keyboardOptions)
}
