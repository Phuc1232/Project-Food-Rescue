package com.foodrescue.app.feature.surprisebox.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.feature.store.presentation.component.StoreErrorMessage
import com.foodrescue.app.feature.surprisebox.domain.*
import kotlinx.coroutines.flow.filterNotNull

@Composable
internal fun OwnerBoxesRoute(storeName: String, vm: OwnerBoxesViewModel, onBack: () -> Unit, onMenu: () -> Unit,
    modifier: Modifier = Modifier) {
    val state = vm.state
    val snackbar = remember { SnackbarHostState() }
    val back = { vm.back(onBack) }
    BackHandler(onBack = back)
    LaunchedEffect(vm) { if (vm.state.loading) vm.load() }
    LaunchedEffect(vm) { snapshotFlow { vm.state.message }.filterNotNull().collect { vm.consumeMessage(); snackbar.showSnackbar(it) } }
    Column(modifier.fillMaxSize()) {
      Box(Modifier.weight(1f).fillMaxWidth()) {
        when {
            state.loading -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.error == null) CircularProgressIndicator() else {
                    StoreErrorMessage(state.error)
                    FoodRescueButton("Thử lại", vm::load, enabled = !state.busy)
                }
            }
            state.page == OwnerBoxPage.TEMPLATE_EDIT -> BoxTemplateEditorScreen(state, back, vm::editTemplate, vm::saveTemplate)
            state.page == OwnerBoxPage.BATCH_EDIT -> SaleBatchEditorScreen(state, back, vm::editBatch, vm::saveBatch)
            state.page == OwnerBoxPage.BATCH_DETAIL && state.selected != null -> SaleBatchDetailScreen(checkNotNull(state.selected), state.now,
                state.busy, state.error, back, { vm.beginBatch(batch = state.selected) }, vm::requestAction)
            else -> OwnerBoxesScreen(storeName, state, vm::section, vm::search, vm::filter, { vm.beginTemplate() }, vm::beginTemplate,
                vm::requestToggle, { vm.beginBatch(it) }, vm::openBatch, onMenu)
        }
        if (state.busy && !state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
      }
      // Reserve layout space so transient feedback never intercepts the final action in a form.
      if (snackbar.currentSnackbarData != null) {
          SnackbarHost(snackbar, Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp))
      }
    }
    if (state.confirmDiscard) AlertDialog(onDismissRequest = vm::keepEditing, title = { Text("Bỏ thay đổi chưa lưu?") },
        text = { Text("Nội dung vừa chỉnh sửa sẽ không được lưu.") },
        confirmButton = { TextButton(vm::discard) { Text("Bỏ thay đổi") } },
        dismissButton = { TextButton(vm::keepEditing) { Text("Tiếp tục chỉnh sửa") } })
    state.pendingAction?.let { action ->
        AlertDialog(onDismissRequest = vm::dismissConfirmation,
            title = { Text(when (action) { BatchAction.PUBLISH -> "Xác nhận mở bán?"; BatchAction.PAUSE -> "Tạm dừng đợt bán?"
                BatchAction.RESUME -> "Tiếp tục đợt bán?"; BatchAction.END -> "Kết thúc đợt bán?" }) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(when (action) {
                    BatchAction.PUBLISH -> "Đợt bán sẽ mở theo lịch đã chọn với số lượng, giá và giờ nhận hiện tại."
                    BatchAction.PAUSE -> "Tạm ngừng nhận đơn mới. Các hộp đã đặt và thông tin nhận hàng được giữ nguyên."
                    BatchAction.RESUME -> "Mở lại số hộp còn lại trong khung giờ bán đã thiết lập."
                    BatchAction.END -> "Đợt bán sẽ ngừng nhận đơn mới và không thể mở lại. Các đơn đã có vẫn cần được xử lý."
                }); StoreErrorMessage(state.error)
            } },
            confirmButton = { TextButton(vm::confirmAction, enabled = !state.busy) { Text("Xác nhận") } },
            dismissButton = { TextButton(vm::dismissConfirmation, enabled = !state.busy) { Text("Để sau") } })
    }
    state.catalog.templates.firstOrNull { it.id == state.toggleTemplateId }?.let { template ->
        AlertDialog(onDismissRequest = vm::dismissConfirmation, title = { Text(if (template.active) "Ngừng sử dụng mẫu?" else "Mở lại mẫu hộp?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (template.active) "Mẫu này sẽ không dùng để mở đợt mới. Các đợt đang bán vẫn giữ nguyên."
                    else "Bạn có thể dùng mẫu này để tạo các đợt bán mới."); StoreErrorMessage(state.error)
            } },
            confirmButton = { TextButton(vm::confirmToggle, enabled = !state.busy) { Text("Xác nhận") } },
            dismissButton = { TextButton(vm::dismissConfirmation, enabled = !state.busy) { Text("Để sau") } })
    }
}
