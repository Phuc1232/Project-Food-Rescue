package com.foodrescue.app.dashboard.owner

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.core.common.LocationSource
import com.foodrescue.app.feature.store.domain.StoreReviewStatus
import com.foodrescue.app.feature.store.domain.StorePayoutAccount
import com.foodrescue.app.feature.store.presentation.*
import com.foodrescue.app.feature.store.presentation.component.*
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

@Composable
internal fun OwnerRoute(email: String, vm: StoreManagementViewModel, onCustomer: () -> Unit, onLogout: () -> Unit,
    locationSource: LocationSource,
    modifier: Modifier = Modifier,
    menuContent: @Composable (String, () -> Unit) -> Unit = { _, _ -> },
    boxesContent: @Composable (String, () -> Unit, () -> Unit) -> Unit = { _, _, _ -> },
    boxesFullscreen: Boolean = false,
    ordersContent: @Composable (String) -> Unit = {},
    ordersFullscreen: Boolean = false,
    employeesContent: @Composable (String, () -> Unit) -> Unit = { _, _ -> },
    revenueContent: @Composable (String, () -> Unit) -> Unit = { _, _ -> },
    revenueFullscreen: Boolean = false,
    summary: OwnerOverviewUiState = OwnerOverviewUiState()) {
    val state = vm.state
    var tab by rememberSaveable { mutableStateOf(OwnerTab.OVERVIEW) }
    var page by rememberSaveable { mutableStateOf(OwnerPage.ROOT) }
    var locationReturn by rememberSaveable { mutableStateOf(OwnerPage.REGISTER) }
    var payoutReturn by rememberSaveable { mutableStateOf(OwnerTab.STORE) }
    val chooseLocation: () -> Unit = { locationReturn = page; page = OwnerPage.LOCATION }
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    val pages = rememberSaveableStateHolder()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val window = LocalActivity.current?.window
    SideEffect { window?.let { WindowCompat.getInsetsController(it, it.decorView).apply {
        isAppearanceLightStatusBars = true; isAppearanceLightNavigationBars = true
    } } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(vm, lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.load(force = true) } }
    LaunchedEffect(state.saved) {
        val result = state.saved ?: return@LaunchedEffect
        vm.consumeSaved()
        when (result) {
            StoreSaved.DRAFT -> onCustomer()
            StoreSaved.APPLICATION -> {
                page = if (state.store?.status == StoreReviewStatus.APPROVED) OwnerPage.ROOT else OwnerPage.REVIEW
                tab = OwnerTab.OVERVIEW
            }
            StoreSaved.PROFILE -> { page = OwnerPage.PROFILE; scope.launch { snackbar.showSnackbar("Đã cập nhật thông tin cửa hàng") } }
            StoreSaved.PAYOUT -> { page = OwnerPage.ROOT; tab = payoutReturn; scope.launch { snackbar.showSnackbar("Đã lưu tài khoản nhận tiền") } }
        }
    }
    val store = state.store
    val approved = store?.status == StoreReviewStatus.APPROVED
    val registering = store?.status == StoreReviewStatus.DRAFT ||
        (store?.status == StoreReviewStatus.CHANGES_REQUIRED && page == OwnerPage.REGISTER)
    val profileDirty = store != null && state.draft != store.profile
    val payoutDirty = store != null && state.payoutDraft != (store.payout ?: StorePayoutAccount())
    val leave: () -> Unit = {
        vm.clearTransient()
        when {
            !approved -> onCustomer()
            page == OwnerPage.EDIT_PROFILE -> page = OwnerPage.PROFILE
            page != OwnerPage.ROOT -> page = OwnerPage.ROOT
            else -> tab = OwnerTab.OVERVIEW
        }
    }
    val back: () -> Unit = {
        if (!state.busy) {
            when {
                page == OwnerPage.LOCATION -> page = locationReturn
                registering && state.step > 0 -> vm.previousStep()
                (registering || page == OwnerPage.EDIT_PROFILE) && profileDirty -> confirmExit = true
                page == OwnerPage.PAYOUT && payoutDirty -> confirmExit = true
                else -> leave()
            }
        }
    }
    BackHandler(enabled = !approved || page != OwnerPage.ROOT || tab != OwnerTab.OVERVIEW, onBack = back)
    val payout: () -> Unit = { payoutReturn = tab; vm.beginPayoutEdit(); page = OwnerPage.PAYOUT }
    Box(modifier.fillMaxSize()) {
        when {
            state.loading || store == null -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.error == null) CircularProgressIndicator() else {
                    StoreErrorMessage(state.error)
                    FoodRescueButton("Thử lại", { vm.load() }, Modifier.padding(top = 16.dp))
                    TextButton(onClick = onCustomer) { Text("Quay lại tài khoản") }
                }
            }
            page == OwnerPage.LOCATION -> StoreLocationPickerRoute(state.draft.location, state.draft.fullAddress, locationSource,
                back, { vm.selectLocation(it); page = locationReturn })
            registering -> StoreProfileFormScreen(state, email, true, vm::edit, vm::accept, vm::nextStep, back, vm::submit, vm::saveDraft,
                onChooseLocation = chooseLocation)
            !approved || page == OwnerPage.REVIEW -> StoreReviewScreen(store, email, back, { vm.beginProfileEdit(); page = OwnerPage.REGISTER })
            page == OwnerPage.PROFILE -> OwnerStoreProfileScreen(store, email, back, { vm.beginProfileEdit(); page = OwnerPage.EDIT_PROFILE })
            page == OwnerPage.EDIT_PROFILE -> StoreProfileFormScreen(state, email, false, vm::edit, vm::accept, vm::nextStep, back, vm::saveProfile, vm::saveDraft,
                onChooseLocation = chooseLocation)
            page == OwnerPage.PAYOUT -> StorePayoutScreen(state, vm::editPayout, back, vm::validatePayout, vm::savePayout)
            page == OwnerPage.MENU -> menuContent(store.profile.name, back)
            page == OwnerPage.ROOT && tab == OwnerTab.BOXES && boxesFullscreen ->
                boxesContent(store.profile.name, { tab = OwnerTab.OVERVIEW }, { page = OwnerPage.MENU })
            page == OwnerPage.ROOT && tab == OwnerTab.ORDERS && ordersFullscreen -> ordersContent(store.profile.name)
            page == OwnerPage.ROOT && tab == OwnerTab.REVENUE && revenueFullscreen -> revenueContent(store.profile.name, payout)
            page == OwnerPage.STAFF -> employeesContent(store.profile.name, back)
            else -> Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FoodRescueBrand()
                        Spacer(Modifier.weight(1f))
                        Text("Chủ cửa hàng", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                bottomBar = { NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    OwnerTab.entries.forEach { destination -> NavigationBarItem(
                        selected = destination == tab, onClick = { tab = destination },
                        icon = { FoodRescueGlyph(destination.icon) },
                        label = { Text(destination.label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary))
                    }
                } },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                    pages.SaveableStateProvider(tab.name) {
                        when (tab) {
                            OwnerTab.OVERVIEW -> OwnerOverviewScreen(store, summary, { page = OwnerPage.PROFILE },
                                { tab = OwnerTab.BOXES }, { tab = OwnerTab.ORDERS }, payout, banks = state.banks)
                            OwnerTab.STORE -> OwnerStoreHubScreen(store, email, { page = OwnerPage.PROFILE }, { page = OwnerPage.REVIEW },
                                payout, { page = OwnerPage.STAFF }, onCustomer, onLogout, onMenu = { page = OwnerPage.MENU })
                            OwnerTab.BOXES -> boxesContent(store.profile.name, { tab = OwnerTab.OVERVIEW }, { page = OwnerPage.MENU })
                            OwnerTab.ORDERS -> ordersContent(store.profile.name)
                            OwnerTab.REVENUE -> revenueContent(store.profile.name, payout)
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 112.dp))
    }
    if (confirmExit) AlertDialog(onDismissRequest = { confirmExit = false }, title = { Text("Rời trang khi chưa lưu?") },
        text = { Text("Các thay đổi trên trang này chưa được lưu.") },
        confirmButton = { TextButton(onClick = { confirmExit = false; leave() }) { Text("Bỏ thay đổi") } },
        dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Tiếp tục chỉnh sửa") } })
}
