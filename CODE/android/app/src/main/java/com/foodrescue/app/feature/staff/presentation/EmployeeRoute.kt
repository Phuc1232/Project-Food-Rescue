package com.foodrescue.app.feature.staff.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.feature.staff.domain.*
import kotlinx.coroutines.delay

@Composable
internal fun EmployeeRoute(storeName: String, vm: EmployeeViewModel, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val state = vm.state
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(vm, lifecycle) { lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        while (true) { vm.refresh(); delay(5_000) }
    } }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); vm.consumeMessage(it) } }
    val back = { vm.back(onClose) }
    BackHandler(onBack = back)
    Box(modifier.fillMaxSize()) {
        when (state.page) {
            EmployeePage.LIST -> EmployeeListScreen(storeName, state, back, vm::beginCreate, vm::search, vm::filter, vm::open, vm::reload, vm::resetFilters)
            EmployeePage.FORM -> key(state.step) { EmployeeFormScreen(storeName, state, back, vm::editName, vm::editEmail, vm::editPhone,
                vm::permission, vm::preset, vm::next, vm::save, vm::reload) }
            EmployeePage.DETAIL -> state.selected?.let { member -> EmployeeDetailScreen(storeName, member, state.canMutate,
                back, vm::beginEdit, vm::requestAccess, vm::reload, state.loadError ?: state.error) }
                ?: FoodRescuePage("Hồ sơ nhân viên", back) {
                    if (state.loading) CircularProgressIndicator()
                    else FoodRescueEmptyState("Chưa tải được hồ sơ", state.loadError ?: "Nhân viên không còn trong danh sách của cửa hàng.", "Tải lại", vm::reload)
                }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 90.dp))
    }
    if (state.confirmDiscard) AlertDialog(onDismissRequest = vm::keepEditing, title = { Text("Bỏ thay đổi chưa lưu?") },
        text = { Text("Thông tin và quyền vừa chọn chưa được lưu.") },
        confirmButton = { TextButton(vm::discard) { Text("Bỏ thay đổi") } },
        dismissButton = { TextButton(vm::keepEditing) { Text("Tiếp tục chỉnh sửa") } })
    state.accessTarget?.let { target ->
        val revoking = target == EmployeeStatus.REVOKED
        AlertDialog(onDismissRequest = vm::dismissAccess,
            title = { Text(if (revoking) "Thu hồi truy cập?" else "Khôi phục truy cập?") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(state.selected?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
                Text(if (revoking) "Nhân viên sẽ không thể truy cập cửa hàng. Hồ sơ và lịch sử vẫn được giữ lại."
                    else "Nhân viên sẽ được truy cập cửa hàng với các quyền dưới đây.")
                if (revoking) {
                    OutlinedTextField(state.reason, vm::reason, enabled = !state.busy, minLines = 2, maxLines = 4,
                        label = { Text("Lý do thu hồi") }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Lý do thu hồi" })
                } else EmployeePermissionSummary(state.selected?.permissions.orEmpty())
                EmployeeError(state.error ?: state.loadError)
            } },
            confirmButton = { TextButton(vm::confirmAccess, enabled = state.canMutate,
                colors = ButtonDefaults.textButtonColors(contentColor = if (revoking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)) {
                Text(if (state.busy) "Đang lưu…" else if (revoking) "Xác nhận thu hồi" else "Xác nhận khôi phục")
            } }, dismissButton = { TextButton(vm::dismissAccess, enabled = !state.busy) { Text("Để sau") } })
    }
}
