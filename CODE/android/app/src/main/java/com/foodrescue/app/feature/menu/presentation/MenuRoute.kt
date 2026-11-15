package com.foodrescue.app.feature.menu.presentation

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.feature.menu.domain.MenuStatus
import com.foodrescue.app.feature.menu.presentation.component.*
import kotlinx.coroutines.flow.filterNotNull

@Composable
internal fun MenuRoute(storeName: String, vm: MenuViewModel, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val state = vm.state
    val snackbar = remember { SnackbarHostState() }
    var previewId by rememberSaveable { mutableStateOf<String?>(null) }
    val preview = (state.menu.documents + state.menu.items.mapNotNull { it.photo } + listOfNotNull(state.draft.photo)).firstOrNull { it.id == previewId }
    val images = arrayOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { vm.importPhoto(it.toString()) } }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { vm.importDocument(it.toString()) } }
    val photo: () -> Unit = { try { photoPicker.launch(images) } catch (_: ActivityNotFoundException) { vm.pickerError() } }
    val document: () -> Unit = { try { documentPicker.launch(images + "application/pdf") } catch (_: ActivityNotFoundException) { vm.pickerError() } }
    val back = { vm.back(onClose) }
    LaunchedEffect(vm) { vm.load() }
    LaunchedEffect(vm) { snapshotFlow { vm.state.message }.filterNotNull().collect { vm.consumeMessage(); snackbar.showSnackbar(it) } }
    BackHandler(onBack = back)
    Box(modifier.fillMaxSize()) {
        when {
            state.loading -> FoodRescuePage("Menu nội bộ", back) {
                if (state.error != null) { MenuError(state.error); FoodRescueButton("Thử lại", vm::load, enabled = !state.busy) }
                else Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            state.page == MenuPage.EDIT -> MenuEditorScreen(state, back, vm::save, vm::editName, vm::editPrice,
                vm::editDescription, vm::editCategory, vm::editUnit, photo, vm::removePhoto)
            state.page == MenuPage.DOCUMENTS -> MenuDocumentsScreen(state.menu.documents, state.busy, state.error, back,
                document, { previewId = it.id }, vm::requestRemoveDocument)
            state.page == MenuPage.DETAIL && state.selected != null -> MenuDetailScreen(checkNotNull(state.selected), state.busy, state.error,
                back, { vm.beginEdit(state.selected) }, { vm.requestStatus(checkNotNull(state.selectedId)) }, { previewId = it.id })
            else -> MenuCatalogScreen(storeName, state, back, { vm.beginEdit() }, vm::openItem, vm::documents,
                vm::search, vm::filterCategory, vm::filterStatus, vm::sort, vm::resetFilters)
        }
        if (state.busy && !state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().statusBarsPadding())
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(bottom = 90.dp, start = 16.dp, end = 16.dp))
    }
    if (state.confirmExit) AlertDialog(onDismissRequest = vm::keepEditing, title = { Text("Bỏ thay đổi chưa lưu?") },
        text = { Text("Thông tin bạn vừa chỉnh sửa sẽ không được lưu.") },
        confirmButton = { TextButton(vm::discardEdit) { Text("Bỏ thay đổi") } },
        dismissButton = { TextButton(vm::keepEditing) { Text("Tiếp tục chỉnh sửa") } })
    state.menu.items.firstOrNull { it.id == state.statusTarget }?.let { item ->
        val stopping = item.status == MenuStatus.ACTIVE
        AlertDialog(onDismissRequest = { if (!state.busy) vm.cancelStatus() },
            title = { Text(if (stopping) "Ngừng kinh doanh món?" else "Kinh doanh lại món?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(if (stopping) "${item.name} vẫn được lưu trong menu để tra cứu hoặc mở lại."
                else "${item.name} sẽ trở lại danh sách món đang kinh doanh."); MenuError(state.error) } },
            confirmButton = { TextButton(vm::confirmStatus, enabled = !state.busy) { Text(if (stopping) "Ngừng kinh doanh" else "Mở lại") } },
            dismissButton = { TextButton(vm::cancelStatus, enabled = !state.busy) { Text("Để sau") } })
    }
    state.menu.documents.firstOrNull { it.id == state.documentToRemove }?.let { asset ->
        AlertDialog(onDismissRequest = { if (!state.busy) vm.cancelRemoveDocument() }, title = { Text("Gỡ tài liệu?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("${asset.name} sẽ được gỡ khỏi cửa hàng. Các món trong menu vẫn được giữ lại."); MenuError(state.error) } },
            confirmButton = { TextButton(vm::removeDocument, enabled = !state.busy) { Text("Gỡ tài liệu") } },
            dismissButton = { TextButton(vm::cancelRemoveDocument, enabled = !state.busy) { Text("Giữ lại") } })
    }
    if (preview != null) MenuAssetPreview(preview) { previewId = null }
}
