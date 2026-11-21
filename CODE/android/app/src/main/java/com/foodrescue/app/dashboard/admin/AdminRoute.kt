package com.foodrescue.app.dashboard.admin

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.feature.store.presentation.*
import com.foodrescue.app.feature.complaint.presentation.*
import com.foodrescue.app.feature.refund.presentation.*
import kotlinx.coroutines.delay
import com.foodrescue.app.feature.account.presentation.*
import com.foodrescue.app.feature.finance.presentation.*

internal enum class AdminFinanceTab(val label: String) { REFUNDS("Hoàn tiền"), SETTLEMENTS("Đối soát"), POLICY("Chính sách") }

internal enum class AdminTab(val label: String, val icon: FoodRescueIcon, val available: Boolean = false) {
    OVERVIEW("Tổng quan", FoodRescueIcon.HOME, true), STORES("Cửa hàng", FoodRescueIcon.STORE, true),
    CASES("Xử lý", FoodRescueIcon.RECEIPT, true), FINANCE("Tài chính", FoodRescueIcon.BANK, true), USERS("Người dùng", FoodRescueIcon.PEOPLE, true),
}

@Composable
internal fun AdminRoute(email: String, vm: StoreApplicationsViewModel, complaints: AdminComplaintsViewModel,
    onLogout: () -> Unit, modifier: Modifier = Modifier, refunds: AdminRefundsViewModel? = null,
    supervision: StoreSupervisionViewModel? = null, users: AdminUsersViewModel? = null,
    settlements: AdminFinanceViewModel? = null, policy: FinancialPolicyViewModel? = null) {
    val state = vm.state
    val cases = complaints.state
    val finance = refunds?.state
    val supervisionState = supervision?.state
    val people = users?.state
    val settlementState = settlements?.state
    val policyState = policy?.state
    var financeTab by rememberSaveable { mutableStateOf(AdminFinanceTab.REFUNDS) }
    var supervising by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(AdminTab.OVERVIEW) }
    var showAccount by rememberSaveable { mutableStateOf(false) }
    var showMap by rememberSaveable(state.selectedKey) { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val snackbar = remember { SnackbarHostState() }
    val window = LocalActivity.current?.window
    LaunchedEffect(settlements, policy, lifecycle, tab, financeTab) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        if (tab == AdminTab.FINANCE) when (financeTab) {
            AdminFinanceTab.SETTLEMENTS -> settlements?.refresh()
            AdminFinanceTab.POLICY -> policy?.refresh()
            AdminFinanceTab.REFUNDS -> Unit
        }
    } }
    LaunchedEffect(settlementState?.notice) { settlementState?.notice?.let { snackbar.showSnackbar(it); settlements.consumeNotice() } }
    LaunchedEffect(policyState?.notice) { policyState?.notice?.let { snackbar.showSnackbar(it); policy.consumeNotice() } }
    SideEffect { window?.let { WindowCompat.getInsetsController(it, it.decorView).apply {
        isAppearanceLightStatusBars = true; isAppearanceLightNavigationBars = true
    } } }
    LaunchedEffect(users, lifecycle, tab) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        if (tab == AdminTab.USERS) users?.refresh()
    } }
    LaunchedEffect(people?.notice) { people?.notice?.let { snackbar.showSnackbar(it); users.consumeNotice() } }
    LaunchedEffect(vm, lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.refresh() } }
    LaunchedEffect(complaints, lifecycle, tab) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { complaints.refresh() } }
    LaunchedEffect(supervision, lifecycle, tab, supervising) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        if (tab == AdminTab.STORES && supervising) supervision?.refresh()
    } }
    LaunchedEffect(supervisionState?.notice) { supervisionState?.notice?.let { snackbar.showSnackbar(it); supervision.consumeNotice() } }
    LaunchedEffect(refunds, lifecycle, tab, financeTab) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        if (tab == AdminTab.FINANCE && financeTab == AdminFinanceTab.REFUNDS && refunds != null) while (true) { refunds.refresh(); delay(3_000) }
    } }
    LaunchedEffect(finance?.notice) { finance?.notice?.let { snackbar.showSnackbar(it); refunds.consumeNotice() } }
    LaunchedEffect(state.notice) { state.notice?.let { snackbar.showSnackbar(it); vm.consumeNotice() } }
    LaunchedEffect(cases.notice) { cases.notice?.let { snackbar.showSnackbar(it); complaints.consumeNotice() } }
    val back: () -> Unit = { if (!state.busy && !cases.busy && finance?.busy != true && supervisionState?.busy != true && people?.busy != true && settlementState?.busy != true && policyState?.busy != true) {
        if (policyState?.draft != null) policy.back()
        else if (settlementState?.selectedKey != null) settlements.back()
        else if (people?.selectedKey != null) users.back()
        else if (supervisionState?.selectedKey != null) supervision.back()
        else if (finance?.selectedKey != null) refunds.back()
        else if (cases.selectedKey != null) complaints.back()
        else if (showMap) showMap = false else if (state.selectedKey != null) vm.back() else tab = AdminTab.OVERVIEW
    } }
    BackHandler(state.selectedKey != null || cases.selectedKey != null || finance?.selectedKey != null || supervisionState?.selectedKey != null || people?.selectedKey != null || settlementState?.selectedKey != null || policyState?.draft != null || tab != AdminTab.OVERVIEW, back)
    Box(modifier.fillMaxSize()) {
        val selected = state.selected
        val detail = supervisionState?.detail
        val box = detail?.boxes?.find { it.id == supervisionState.boxId }
        val menuItem = detail?.menu?.items?.find { it.id == supervisionState.menuId }
        when {
            policyState?.draft != null -> FinancialPolicyEditorScreen(policyState, back, policy::change, policy::review, policy::confirm, policy::dismiss)
            settlementState?.selected != null && settlementState.decision != null -> AdminSettlementDecisionScreen(settlementState, back,
                settlements::reason, settlements::reference, settlements::review, settlements::confirm, settlements::dismiss)
            settlementState?.selected != null -> AdminSettlementDetailScreen(settlementState, back, settlements::prepare, settlements::refresh)
            people?.selected != null && people.draft != null -> AdminUserDecisionScreen(people, back, users::reason,
                users::review, users::confirm, users::dismiss)
            people?.selected != null -> AdminUserDetailScreen(people, back, users::prepare, users::refresh)
            supervisionState?.draft != null -> StoreRestrictionDecisionScreen(supervisionState, back,
                supervision::note, supervision::review, supervision::confirm, supervision::dismiss)
            box != null -> SupervisedPackedBoxScreen(box, detail, back)
            menuItem != null -> SupervisedMenuItemScreen(menuItem, back)
            supervisionState?.selectedKey != null -> StoreSupervisionDetailScreen(supervisionState, back,
                supervision::section, supervision::prepare, supervision::openBox, supervision::openMenu, supervision::refresh)
            finance?.selected != null && finance.approve != null -> AdminRefundDecisionScreen(finance, back, refunds::note,
                refunds::review, refunds::confirm, refunds::dismiss)
            finance?.selected != null -> AdminRefundDetailScreen(finance, back, refunds::prepare, refunds::refresh)
            cases.selected != null && cases.decision != null -> AdminComplaintDecisionScreen(cases, back, complaints::decide,
                complaints::note, complaints::amount, complaints::prepareConfirmation, complaints::confirm, complaints::dismissConfirmation)
            cases.selected != null -> AdminComplaintDetailScreen(cases, back, complaints::receive, complaints::decide, complaints::refresh)
            showMap && selected != null -> StoreApplicationMapScreen(selected, { showMap = false })
            state.decision != null && selected != null -> StoreApplicationDecisionScreen(state, vm::note,
                vm::cancelDecision, vm::prepareConfirmation, vm::confirm, vm::dismissConfirmation)
            selected != null -> StoreApplicationDetailScreen(selected, state.busy, state.error, back, vm::decide,
                { showMap = true }, vm::refresh)
            else -> AdminShell(tab, { tab = it }, { showAccount = true }) {
                when (tab) {
                    AdminTab.OVERVIEW -> AdminOverviewScreen(state, { vm.filter(it); supervising = false; tab = AdminTab.STORES },
                        { vm.open(it) }, { vm.refresh(); complaints.refresh() }, complaintSummary = {
                            AdminComplaintOverviewCard(cases, { tab = AdminTab.CASES }, complaints::refresh)
                        })
                    AdminTab.STORES -> Column(Modifier.fillMaxSize()) {
                        if (supervision != null) Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilterChip(!supervising, { supervising = false; vm.refresh() }, label = { Text("Xét duyệt") })
                            FilterChip(supervising, { supervising = true }, label = { Text("Giám sát") })
                        }
                        Box(Modifier.weight(1f)) {
                            if (supervising && supervisionState != null) StoreSupervisionScreen(supervisionState,
                                supervision::search, supervision::filter, supervision::open, supervision::refresh)
                            else StoreApplicationsScreen(state, vm::search, vm::filter, vm::open, vm::refresh)
                        }
                    }
                    AdminTab.CASES -> AdminComplaintsScreen(cases, complaints::search, complaints::filter, complaints::open, complaints::refresh)
                    AdminTab.FINANCE -> Column(Modifier.fillMaxSize()) {
                        if (settlements != null && policy != null) FlowRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AdminFinanceTab.entries.forEach { section -> FilterChip(financeTab == section,
                                { financeTab = section }, label = { Text(section.label) }) }
                        }
                        Box(Modifier.weight(1f)) { when (financeTab) {
                            AdminFinanceTab.REFUNDS -> if (refunds != null && finance != null) AdminRefundsScreen(finance,
                                refunds::search, refunds::filter, refunds::open, refunds::refresh)
                            AdminFinanceTab.SETTLEMENTS -> if (settlements != null && settlementState != null) AdminSettlementsScreen(
                                settlementState, settlements::search, settlements::filter, settlements::open, settlements::refresh)
                            AdminFinanceTab.POLICY -> if (policy != null && policyState != null) FinancialPolicyScreen(policyState, policy::edit, policy::refresh)
                        } }
                    }
                    AdminTab.USERS -> if (users != null && people != null) AdminUsersScreen(people,
                        users::search, users::role, users::status, users::open, users::refresh)
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 110.dp))
    }
    if (showAccount) AlertDialog(onDismissRequest = { showAccount = false }, title = { Text("Quản trị viên") },
        text = { Text(email) }, confirmButton = { TextButton({ showAccount = false; onLogout() }, enabled = !state.busy && !cases.busy && finance?.busy != true && supervisionState?.busy != true && people?.busy != true && settlementState?.busy != true && policyState?.busy != true) { Text("Đăng xuất") } },
        dismissButton = { TextButton({ showAccount = false }) { Text("Đóng") } })
}

@Composable
internal fun AdminShell(tab: AdminTab, onTab: (AdminTab) -> Unit, onAccount: () -> Unit,
    modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Scaffold(modifier, containerColor = MaterialTheme.colorScheme.background, topBar = {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FoodRescueBrand()
            Spacer(Modifier.weight(1f))
            Text("Quản trị viên", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onAccount) { FoodRescueGlyph(FoodRescueIcon.PERSON, description = "Tài khoản quản trị viên") }
        }
    }, bottomBar = { NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        AdminTab.entries.forEach { item -> NavigationBarItem(selected = item == tab, onClick = { onTab(item) }, enabled = item.available,
            icon = { FoodRescueGlyph(item.icon) }, label = { Text(item.label, style = MaterialTheme.typography.labelMedium, maxLines = 2, textAlign = TextAlign.Center) },
            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary)) }
    } }) { padding -> Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) { content() } }
}
