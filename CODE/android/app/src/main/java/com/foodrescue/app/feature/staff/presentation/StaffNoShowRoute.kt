package com.foodrescue.app.feature.staff.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.foodrescue.app.feature.surprisebox.domain.PackedBox
import kotlinx.coroutines.delay

@Composable
internal fun StaffNoShowRoute(vm: StaffNoShowViewModel, onClose: () -> Unit, onInspect: (PackedBox) -> Unit, modifier: Modifier = Modifier) {
    val state = vm.state
    val lifecycle = LocalLifecycleOwner.current
    val focus = LocalFocusManager.current
    LaunchedEffect(vm, lifecycle) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { vm.refresh(); delay(5_000) }
        }
    }
    val close = { if (!state.busy) { vm.close(); onClose() } }
    BackHandler { close() }
    key(state.order?.noShow) {
        StaffNoShowScreen(state, close, vm::edit, { focus.clearFocus(); vm.requestConfirmation() }, vm::reload, onInspect, modifier)
    }
    if (state.confirm) AlertDialog(onDismissRequest = vm::dismissConfirmation,
        title = { Text("Ghi nhận khách không đến?") },
        text = { Text("Đơn ${state.order?.reference.orEmpty()} sẽ được ghi nhận No-show. " +
            if (state.draft.boxIds.isEmpty()) "Không có hộp riêng được liên kết với lần xác minh này."
            else "${state.draft.boxIds.size} hộp đã chọn sẽ chờ kiểm tra lại. Lịch sử xác minh được lưu theo nhân viên.") },
        confirmButton = { TextButton(vm::finish, enabled = !state.busy) { Text("Xác nhận No-show") } },
        dismissButton = { TextButton(vm::dismissConfirmation, enabled = !state.busy) { Text("Xem lại") } })
}
