package com.foodrescue.app.feature.order.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*

@Composable
internal fun OwnerOrdersRoute(storeName: String, vm: OwnerOrdersViewModel, modifier: Modifier = Modifier) {
    val state = vm.state
    var discard by rememberSaveable { mutableStateOf(false) }
    val back = {
        if (!state.busy) {
            if (state.page == OwnerOrderPage.CANCELLATION && (state.draft?.reason != null || state.draft?.note?.isNotBlank() == true)) discard = true
            else vm.back()
        }
    }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }
    BackHandler(state.page != OwnerOrderPage.LIST, onBack = back)
    Box(modifier.fillMaxSize()) {
        val order = state.selected
        when {
            state.page == OwnerOrderPage.LIST -> OwnerOrdersScreen(storeName, state, vm::search, vm::filter, vm::pickup, vm::open, vm::reload)
            order == null || state.loadError != null -> FoodRescuePage("Chi tiết đơn hàng", back) {
                if (state.loading) CircularProgressIndicator()
                else FoodRescueEmptyState("Chưa tải được đơn", state.loadError ?: "Đơn không còn trong danh sách của cửa hàng.", "Tải lại", vm::reload)
            }
            state.page == OwnerOrderPage.CANCELLATION && state.draft != null -> OwnerCancellationScreen(order, state.draft,
                state.busy, state.error, vm::reason, vm::note, back, vm::review)
            else -> OwnerOrderDetailScreen(order, state.now, back, vm::beginCancellation)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp))
    }
    if (state.confirm && state.loadError == null) AlertDialog(onDismissRequest = vm::dismissConfirmation,
        title = { Text("Gửi yêu cầu hủy đơn?") },
        text = { Text("${state.selected?.reference.orEmpty()}\n${state.draft?.reason?.label.orEmpty()}\nYêu cầu sẽ được chuyển sang chờ xử lý.") },
        confirmButton = { TextButton(onClick = vm::submit, enabled = !state.busy) { Text(if (state.busy) "Đang gửi…" else "Gửi yêu cầu") } },
        dismissButton = { TextButton(onClick = vm::dismissConfirmation, enabled = !state.busy) { Text("Kiểm tra lại") } })
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Bỏ nội dung chưa gửi?") },
        text = { Text("Lý do và nội dung yêu cầu này chưa được gửi.") },
        confirmButton = { TextButton({ discard = false; vm.back() }) { Text("Bỏ nội dung") } },
        dismissButton = { TextButton({ discard = false }) { Text("Tiếp tục nhập") } })
}
