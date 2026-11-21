package com.foodrescue.app.feature.refund.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.core.util.money
import com.foodrescue.app.feature.refund.domain.*
import com.foodrescue.app.feature.refund.presentation.component.label
import kotlinx.coroutines.flow.collectLatest

@Composable
internal fun AdminRefundDecisionScreen(state: AdminRefundsState, onBack: () -> Unit, onNote: (String) -> Unit,
    onReview: () -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val row = state.selected ?: return
    val approve = state.approve ?: return
    val noteRequester = remember { BringIntoViewRequester() }
    val errorRequester = remember { BringIntoViewRequester() }
    var noteFocused by remember { mutableStateOf(false) }
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    LaunchedEffect(noteFocused, ime, density) {
        if (noteFocused) snapshotFlow { ime.getBottom(density) }.collectLatest { height ->
            if (height > 0) {
                withFrameNanos { }
                noteRequester.bringIntoView()
            }
        }
    }
    LaunchedEffect(state.noteError) {
        if (state.noteError != null) noteRequester.bringIntoView()
    }
    FoodRescuePage(if (approve) "Xác nhận khoản hoàn" else "Từ chối yêu cầu hủy", onBack, modifier, footer = {
        FoodRescueButton("Xem lại quyết định", onReview, Modifier.fillMaxWidth(), enabled = !state.busy)
    }) {
        Text(row.order?.reference.orEmpty(), style = MaterialTheme.typography.titleLarge)
        Text(row.order?.boxSnapshot?.store.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        FoodRescueCard {
            Text(row.source.label, style = MaterialTheme.typography.titleMedium)
            if (approve) {
                Text(money(row.amount), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                Text("Hoàn về phương thức QR VNPAY của giao dịch gốc.")
                if (row.source == RefundSource.STORE_CANCELLATION && row.status == AdminRefundStatus.PENDING) Text("Đơn hàng sẽ được hủy sau khi xác nhận.")
                if (row.status == AdminRefundStatus.FAILED) Text("Tiếp tục xử lý trên cùng yêu cầu; không tạo thêm khoản hoàn.")
            } else Text("Đơn hàng tiếp tục được giữ để cửa hàng xử lý bàn giao. Không phát sinh giao dịch hoàn tiền.")
        }
        OutlinedTextField(state.note, onNote, Modifier.fillMaxWidth().bringIntoViewRequester(noteRequester)
            .onFocusChanged { noteFocused = it.isFocused }, enabled = !state.busy, minLines = 4, maxLines = 8,
            label = { Text("Lý do xử lý") }, placeholder = { Text("Nêu căn cứ xác nhận hoặc từ chối yêu cầu") },
            isError = state.noteError != null, supportingText = { Text(state.noteError ?: "${state.note.length}/500") })
        state.error?.let {
            Text(it, Modifier.bringIntoViewRequester(errorRequester), color = MaterialTheme.colorScheme.error)
            LaunchedEffect(it) { errorRequester.bringIntoView() }
        }
    }
    if (state.confirming) AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (approve) "Gửi yêu cầu hoàn tiền?" else "Từ chối hủy đơn?") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(row.order?.reference.orEmpty(), style = MaterialTheme.typography.titleSmall)
                if (approve) Text(money(row.amount), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Text(state.note)
                Text("Quyết định và lý do sẽ được lưu vào lịch sử xử lý.", style = MaterialTheme.typography.bodySmall)
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }, confirmButton = { TextButton(onConfirm, enabled = !state.busy) { Text(if (approve) "Xác nhận hoàn tiền" else "Xác nhận từ chối") } },
        dismissButton = { TextButton(onDismiss, enabled = !state.busy) { Text("Xem lại") } })
}
