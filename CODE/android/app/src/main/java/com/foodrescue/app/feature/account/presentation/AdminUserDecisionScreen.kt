package com.foodrescue.app.feature.account.presentation

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
import com.foodrescue.app.feature.account.domain.*
import kotlinx.coroutines.flow.collectLatest

@Composable
internal fun AdminUserDecisionScreen(state: AdminUsersState, onBack: () -> Unit, onReason: (String) -> Unit,
    onReview: () -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val user = state.selected ?: return
    val draft = state.draft ?: return
    val requester = remember { BringIntoViewRequester() }
    val errorRequester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val ime = WindowInsets.ime; val density = LocalDensity.current
    LaunchedEffect(focused, ime, density) {
        if (focused) snapshotFlow { ime.getBottom(density) }.collectLatest {
            if (it > 0) { withFrameNanos { }; requester.bringIntoView() }
        }
    }
    LaunchedEffect(state.noteError) { if (state.noteError != null) requester.bringIntoView() }
    FoodRescuePage(draft.status.action, onBack, modifier, footer = {
        FoodRescueButton("Xem lại thay đổi", onReview, Modifier.fillMaxWidth(), enabled = !state.busy)
    }) {
        Text(user.name, style = MaterialTheme.typography.headlineSmall)
        Text(user.email ?: user.reference)
        FoodRescueCard {
            Text("Ảnh hưởng đến truy cập", style = MaterialTheme.typography.titleMedium)
            Text(if (draft.status == AccountStatus.SUSPENDED)
                "Chặn đăng nhập và truy cập trên mọi vai trò của tài khoản này. Người dùng cần đăng nhập lại sau khi được mở khóa."
            else "Cho phép đăng nhập lại. Quyền nhân viên đã bị cửa hàng thu hồi và hạn chế bán của cửa hàng vẫn giữ nguyên.")
            HorizontalDivider()
            Text("${user.pendingOrders} đơn đã thanh toán chưa hoàn tất.", style = MaterialTheme.typography.titleSmall)
            Text("Đơn hàng, khiếu nại và lịch sử giao dịch được giữ nguyên; không tự động hủy đơn hoặc hoàn tiền.",
                style = MaterialTheme.typography.bodySmall)
            if (AccountRole.OWNER in user.roles) Text("Cửa hàng vẫn có thể tiếp tục vận hành qua nhân viên còn quyền. Dùng Giám sát cửa hàng nếu cần ngừng bán.",
                style = MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(draft.reason, onReason, Modifier.fillMaxWidth().bringIntoViewRequester(requester)
            .onFocusChanged { focused = it.isFocused }, enabled = !state.busy, minLines = 4, maxLines = 8,
            label = { Text("Lý do thay đổi trạng thái") }, placeholder = { Text("Nêu căn cứ và nội dung cần thông báo cho người dùng") },
            isError = state.noteError != null, supportingText = { Text(state.noteError ?: "${draft.reason.length}/500 · Lưu trong nhật ký") })
        state.error?.let {
            AccountError(it, Modifier.bringIntoViewRequester(errorRequester))
            LaunchedEffect(it) { errorRequester.bringIntoView() }
        }
    }
    if (state.confirming) AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Xác nhận thay đổi?") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(draft.status.action, style = MaterialTheme.typography.titleMedium)
                Text(user.name); Text(user.email ?: user.reference); Text(draft.reason)
                Text("Thao tác và người xử lý sẽ được lưu vào nhật ký.", style = MaterialTheme.typography.bodySmall)
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }, confirmButton = { TextButton(onConfirm, enabled = !state.busy) { Text("Xác nhận lưu") } },
        dismissButton = { TextButton(onDismiss, enabled = !state.busy) { Text("Xem lại") } })
}

