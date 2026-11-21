package com.foodrescue.app.feature.finance.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.core.util.money
import com.foodrescue.app.feature.finance.domain.*

@Composable
internal fun AdminSettlementDecisionScreen(state: AdminFinanceState, onBack: () -> Unit,
    onReason: (String) -> Unit, onReference: (String) -> Unit, onReview: () -> Unit,
    onConfirm: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val item = state.selected ?: return; val draft = state.decision ?: return
    FoodRescuePage(draft.action.label, onBack, modifier, footer = {
        FoodRescueButton("Kiểm tra và xác nhận", onReview, Modifier.fillMaxWidth(), enabled = !state.busy)
    }) {
        Text(item.storeName, style = MaterialTheme.typography.headlineSmall)
        SettlementMoneyCard(item.record)
        Text(when (draft.action) {
            SettlementAction.CONFIRM -> if (item.record.net == 0L) "Kỳ được bù trừ hết, không phát sinh chuyển tiền. Xác nhận để chốt số liệu và lịch sử."
                else "Chốt danh sách đơn, hoa hồng và tài khoản nhận tiền cho kỳ này."
            SettlementAction.START -> "Đưa khoản chi trả sang trạng thái đang chuyển. Chỉ ghi nhận đã chi khi có kết quả và mã giao dịch."
            SettlementAction.COMPLETE -> "Kiểm tra tiền đã chuyển thành công đến đúng tài khoản trước khi ghi nhận."
            SettlementAction.FAIL -> "Ghi rõ nguyên nhân. Khoản chi trả được giữ lại để kiểm tra và thử lại."
            SettlementAction.VOID -> "Hủy kỳ chưa chi. Các đơn đủ điều kiện sẽ trở về danh sách chờ đối soát; lịch sử kỳ này được giữ lại."
        }, style = MaterialTheme.typography.bodyMedium)
        if (draft.action == SettlementAction.COMPLETE) OutlinedTextField(draft.transferReference, onReference,
            Modifier.fillMaxWidth(), enabled = !state.busy, singleLine = true, label = { Text("Mã giao dịch chuyển tiền") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            isError = state.attempted && !validTransferReference(draft.transferReference),
            supportingText = { Text("6–80 ký tự: chữ, số, dấu chấm, gạch ngang, gạch dưới hoặc /") })
        OutlinedTextField(draft.reason, onReason, Modifier.fillMaxWidth(), enabled = !state.busy, minLines = 3,
            label = { Text("Nội dung xác nhận") }, isError = state.attempted && !validFinanceReason(draft.reason),
            supportingText = { Text("${draft.reason.length}/500 · Tối thiểu 10 ký tự") })
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    if (state.confirming) AlertDialog(onDismissRequest = onDismiss, title = { Text(draft.action.label + "?") },
        text = { Text("${item.storeName}\n${money(item.record.net)}\n${draft.reason}" +
            if (draft.transferReference.isNotBlank()) "\nMã giao dịch: ${draft.transferReference}" else "") },
        confirmButton = { TextButton(onConfirm, enabled = !state.busy) { Text("Xác nhận") } },
        dismissButton = { TextButton(onDismiss, enabled = !state.busy) { Text("Kiểm tra lại") } })
}
