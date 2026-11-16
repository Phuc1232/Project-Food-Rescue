package com.foodrescue.app.dashboard.staff

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.feature.staff.presentation.*
import com.foodrescue.app.feature.surprisebox.domain.canInspect
import kotlinx.coroutines.delay

@Composable
internal fun StaffRoute(vm: StaffPackingViewModel, handover: StaffHandoverViewModel, condition: StaffBoxConditionViewModel,
    noShow: StaffNoShowViewModel, onCustomer: () -> Unit, onLogout: () -> Unit, modifier: Modifier = Modifier) {
    val state = vm.state
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(vm, lifecycle) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { vm.refresh(); delay(5_000) }
        }
    }
    BackHandler(enabled = state.page != StaffPage.OVERVIEW || state.busy) { vm.back() }
    when {
        condition.state.draft.boxId.isNotBlank() -> StaffBoxConditionRoute(condition, { vm.reload(); noShow.reload(); handover.reload() }, modifier)
        noShow.state.draft.orderId.isNotBlank() -> StaffNoShowRoute(noShow, { handover.close(); handover.reload(); vm.reload() }, condition::open, modifier)
        state.loading || state.workspace.access?.allowed != true -> StaffAccessScreen(
            StaffAccessUiState(state.loading, state.workspace.access, state.error), vm::reload, onCustomer, onLogout, modifier)
        state.page == StaffPage.PACKING -> key(state.step) {
            StaffPackingScreen(state, vm::back, vm::quantity, vm::search,
                { vm.safety(it.expiresAt, it.storage, it.foodChecked, it.packagingChecked, it.allergensChecked) }, vm::next, vm::finish,
                vm::updateMenuSelections, vm::reload, { vm.navigate(StaffPage.OVERVIEW) }, vm::requestDiscard, modifier)
        }
        state.page == StaffPage.DETAIL && state.selectedBox != null -> PackedBoxDetailScreen(checkNotNull(state.selectedBox), state.now,
            vm::back, { vm.navigate(StaffPage.OVERVIEW) }, modifier,
            onInspect = if (state.canInspect && state.selectedBox?.canInspect() == true) ({ condition.open(checkNotNull(state.selectedBox)) }) else null)
        state.page == StaffPage.HANDOVER && handover.state.draft.orderId.isNotBlank() -> StaffHandoverRoute(handover, modifier, noShow::open)
        else -> StaffShell(state, vm::navigate, vm::reload, modifier) { contentModifier ->
            when (state.page) {
                StaffPage.MENU -> StaffMenuScreen(state, vm::search, contentModifier)
                StaffPage.BOXES -> StaffBoxesScreen(state, vm::search, vm::openBox, vm::filterBoxes, contentModifier)
                StaffPage.ACCOUNT -> StaffAccountScreen(state, onCustomer, onLogout, contentModifier)
                StaffPage.HANDOVER -> StaffHandoverRoute(handover, contentModifier, noShow::open)
                else -> StaffDashboardScreen(state, vm::begin, vm::resume, vm::openBox, { vm.navigate(StaffPage.HANDOVER) }, contentModifier)
            }
        }
    }
    if (state.confirmDiscard) AlertDialog(onDismissRequest = vm::keepDraft,
        title = { Text("Bỏ bản nháp đóng gói?") }, text = { Text("Các món và mục kiểm tra của hộp này sẽ được xóa khỏi bản nháp.") },
        confirmButton = { TextButton(vm::discard) { Text("Bỏ bản nháp") } }, dismissButton = { TextButton(vm::keepDraft) { Text("Tiếp tục làm") } })
}

@Composable
private fun StaffShell(state: StaffPackingUiState, onNavigate: (StaffPage) -> Unit, onRetry: () -> Unit,
    modifier: Modifier = Modifier, content: @Composable (Modifier) -> Unit) {
    val tabs = listOf(Triple(StaffPage.OVERVIEW, "Ca làm", FoodRescueIcon.HOME), Triple(StaffPage.MENU, "Menu", FoodRescueIcon.RECEIPT),
        Triple(StaffPage.BOXES, "Hộp", FoodRescueIcon.BAG), Triple(StaffPage.HANDOVER, "Bàn giao", FoodRescueIcon.CHECK))
    Scaffold(modifier, containerColor = MaterialTheme.colorScheme.background, topBar = {
        Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("FOODRESCUE  /  NHÂN VIÊN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(state.workspace.access?.storeName.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                IconButton({ onNavigate(StaffPage.ACCOUNT) }, Modifier.size(36.dp)) { FoodRescueGlyph(FoodRescueIcon.PERSON, description = "Mở tài khoản nhân viên") }
            }
        }
    }, bottomBar = {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            tabs.forEach { (page, label, icon) -> NavigationBarItem(selected = state.page == page, onClick = { onNavigate(page) },
                icon = { FoodRescueGlyph(icon) }, label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall) }) }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            state.error?.let { error -> Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(error, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onRetry) { Text("Tải lại") }
                }
            } }
            content(Modifier.weight(1f))
        }
    }
}
