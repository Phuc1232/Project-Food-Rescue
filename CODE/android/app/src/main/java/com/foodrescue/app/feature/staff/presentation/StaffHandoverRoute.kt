package com.foodrescue.app.feature.staff.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

@Composable
internal fun StaffHandoverRoute(vm: StaffHandoverViewModel, modifier: Modifier = Modifier, onNoShow: ((String) -> Unit)? = null) {
    val state = vm.state
    val lifecycle = LocalLifecycleOwner.current
    val focus = LocalFocusManager.current
    LaunchedEffect(vm, lifecycle) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { vm.refresh(); delay(5_000) }
        }
    }
    BackHandler(state.draft.orderId.isNotBlank() || state.busy) {
        if (state.failureForm) vm.cancelFailure() else vm.close()
    }
    key(state.draft.orderId, state.failureForm, state.order?.collectedAtMillis) {
        when {
            state.failureForm -> StaffHandoverFailureScreen(state, vm::cancelFailure, vm::editFailure,
                { focus.clearFocus(); vm.saveFailure() }, vm::reload, modifier)
            state.order != null -> StaffHandoverDetailScreen(state, vm::close, vm::editCode,
                { focus.clearFocus(); vm.verifyCode() }, vm::toggleBox,
                { focus.clearFocus(); vm.requestConfirmation() }, vm::showFailure, vm::reload, modifier, onNoShow)
            else -> StaffHandoverQueueScreen(state, vm::search, vm::filter, vm::open, vm::reload, modifier)
        }
    }
    if (state.confirm) AlertDialog(onDismissRequest = vm::dismissConfirmation,
        title = { Text("Hoàn tất bàn giao?") },
        text = { Text("Xác nhận khách đã nhận đủ ${state.order?.quantity ?: 0} hộp vừa chọn. Đơn hàng sẽ chuyển sang đã nhận.") },
        confirmButton = { TextButton(vm::finish, enabled = !state.busy) { Text(if (state.busy) "Đang lưu…" else "Xác nhận đã giao") } },
        dismissButton = { TextButton(vm::dismissConfirmation, enabled = !state.busy) { Text("Kiểm tra lại") } })
}
