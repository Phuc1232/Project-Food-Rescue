package com.foodrescue.app.feature.store.presentation

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
import com.foodrescue.app.feature.store.presentation.component.*
import kotlinx.coroutines.flow.collectLatest

@Composable
internal fun StoreRestrictionDecisionScreen(state: StoreSupervisionState, onBack: () -> Unit, onNote: (String) -> Unit,
    onReview: () -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val draft = state.draft ?: return
    val detail = state.detail ?: return
    val noteRequester = remember { BringIntoViewRequester() }
    val errorRequester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    LaunchedEffect(focused, ime, density) {
        if (focused) snapshotFlow { ime.getBottom(density) }.collectLatest { height ->
            if (height > 0) { withFrameNanos { }; noteRequester.bringIntoView() }
        }
    }
    LaunchedEffect(state.noteError) { if (state.noteError != null) noteRequester.bringIntoView() }
    FoodRescuePage(draft.action.label, onBack, modifier, footer = {
        FoodRescueButton("Xem lại quyết định", onReview, Modifier.fillMaxWidth(), enabled = !state.busy)
    }) {
        Text(detail.entry.store.profile.name, style = MaterialTheme.typography.headlineSmall)
        Text(draft.batch?.let { "${it.template.name} · Đợt ${it.id.takeLast(8).uppercase()}" } ?: "Phạm vi: Toàn cửa hàng",
            style = MaterialTheme.typography.titleSmall)
        FoodRescueCard {
            StoreSectionTitle("Ảnh hưởng của quyết định")
            Text(draft.action.impact)
            HorizontalDivider()
            Text("Đơn đã thanh toán vẫn được phục vụ. Lượt giữ hộp có trước quyết định tiếp tục có hiệu lực đến hạn; không phát sinh hủy đơn hoặc hoàn tiền tự động.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(draft.note, onNote, Modifier.fillMaxWidth().bringIntoViewRequester(noteRequester)
            .onFocusChanged { focused = it.isFocused }, enabled = !state.busy, minLines = 4, maxLines = 8,
            label = { Text("Lý do và yêu cầu xử lý") }, placeholder = { Text("Nêu căn cứ kiểm tra và nội dung chủ cửa hàng cần biết") },
            isError = state.noteError != null, supportingText = { Text(state.noteError ?: "${draft.note.length}/500 · Hiển thị cho chủ cửa hàng") })
        state.error?.let {
            StoreErrorMessage(it, Modifier.bringIntoViewRequester(errorRequester))
            LaunchedEffect(it) { errorRequester.bringIntoView() }
        }
    }
    if (state.confirming) AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Xác nhận quyết định?") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(draft.action.label, style = MaterialTheme.typography.titleMedium)
                Text(detail.entry.store.profile.name)
                draft.batch?.let { Text("${it.template.name} · ${it.id.takeLast(8).uppercase()}") }
                Text(draft.note)
                Text("Quyết định, lý do và tài khoản xử lý sẽ được lưu vào nhật ký.", style = MaterialTheme.typography.bodySmall)
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }, confirmButton = { TextButton(onConfirm, enabled = !state.busy) { Text("Xác nhận lưu") } },
        dismissButton = { TextButton(onDismiss, enabled = !state.busy) { Text("Xem lại") } })
}
