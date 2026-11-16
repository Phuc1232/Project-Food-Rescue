package com.foodrescue.app.feature.staff.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.foodrescue.app.feature.surprisebox.domain.InspectionResult
import kotlinx.coroutines.delay

@Composable
internal fun StaffBoxConditionRoute(vm: StaffBoxConditionViewModel, onClose: () -> Unit, modifier: Modifier = Modifier) {
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
    key(state.saved) {
        StaffBoxConditionScreen(state, close, vm::edit, { focus.clearFocus(); vm.requestConfirmation() }, vm::reload, vm::reviewLatest, modifier)
    }
    if (state.confirm) {
        val rejected = state.draft.result == InspectionResult.REJECTED
        AlertDialog(onDismissRequest = vm::dismissConfirmation,
            title = { Text(if (rejected) "Loại hộp khỏi bàn giao?" else "Lưu kết quả đạt yêu cầu?") },
            text = { Text(if (rejected) "${state.box?.code.orEmpty()} sẽ không thể được giao cho khách. Lý do và người kiểm tra sẽ được lưu trong lịch sử."
                else "Xác nhận bạn đã kiểm tra trực tiếp đủ bốn mục. Hạn sử dụng của hộp được giữ nguyên.") },
            confirmButton = { TextButton(vm::finish, enabled = !state.busy) { Text(if (rejected) "Xác nhận loại hộp" else "Xác nhận đạt yêu cầu") } },
            dismissButton = { TextButton(vm::dismissConfirmation, enabled = !state.busy) { Text("Xem lại") } })
    }
}
