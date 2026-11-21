package com.foodrescue.app.app.navigation

import com.foodrescue.app.feature.store.presentation.StoreApplicationsViewModel
import com.foodrescue.app.dashboard.admin.AdminRoute
import androidx.compose.runtime.DisposableEffect
import com.foodrescue.app.feature.account.domain.CustomerProfile
import com.foodrescue.app.feature.account.presentation.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.util.Locale
import com.foodrescue.app.app.session.SessionViewModel
import com.foodrescue.app.dashboard.customer.CustomerRoute
import com.foodrescue.app.dashboard.owner.OwnerRoute
import com.foodrescue.app.dashboard.owner.OwnerOverviewUiState
import com.foodrescue.app.feature.surprisebox.presentation.OwnerBoxesRoute
import com.foodrescue.app.feature.surprisebox.presentation.OwnerBoxesViewModel
import com.foodrescue.app.feature.surprisebox.presentation.OwnerBoxPage
import com.foodrescue.app.feature.store.presentation.StoreManagementViewModel
import com.foodrescue.app.feature.menu.presentation.MenuRoute
import com.foodrescue.app.feature.menu.presentation.MenuViewModel
import com.foodrescue.app.di.RepositoryModule
import com.foodrescue.app.feature.auth.presentation.AuthRoute
import com.foodrescue.app.feature.auth.presentation.AuthViewModel
import com.foodrescue.app.feature.discovery.presentation.DiscoveryViewModel
import com.foodrescue.app.feature.order.presentation.BookingViewModel
import com.foodrescue.app.feature.order.presentation.OwnerOrdersViewModel
import com.foodrescue.app.feature.order.presentation.OwnerOrdersRoute
import com.foodrescue.app.feature.order.presentation.OwnerOrderPage
import com.foodrescue.app.feature.order.domain.OwnerOrderFilter
import com.foodrescue.app.feature.complaint.presentation.ComplaintViewModel
import com.foodrescue.app.feature.complaint.presentation.AdminComplaintsViewModel
import com.foodrescue.app.feature.staff.presentation.EmployeeViewModel
import com.foodrescue.app.feature.staff.presentation.EmployeeRoute
import com.foodrescue.app.feature.staff.presentation.StaffPackingViewModel
import com.foodrescue.app.feature.staff.presentation.StaffHandoverViewModel
import com.foodrescue.app.feature.staff.presentation.StaffBoxConditionViewModel
import com.foodrescue.app.feature.staff.presentation.StaffNoShowViewModel
import com.foodrescue.app.dashboard.staff.StaffRoute
import com.foodrescue.app.feature.finance.presentation.RevenueViewModel
import com.foodrescue.app.feature.finance.presentation.RevenueRoute
import com.foodrescue.app.feature.finance.presentation.RevenuePage

@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val session: SessionViewModel = viewModel()
    val auth: AuthViewModel = viewModel()
    val profile = session.profile
    val context = LocalContext.current
    var accountRequest by rememberSaveable { mutableStateOf<List<String>?>(null) }
    val accounts = remember(context) { RepositoryModule.accountSessionRepository(context) }
    if (profile != null) {
        val access: AccountAccessViewModel = viewModel(key = "account.access:${profile.email}:${session.role}",
            factory = RepositoryModule.accountAccessViewModelFactory)
        AccountAccessGate(access, profile.email, session.accessVersion,
            { accountRequest = null; auth.reset(); session.signOut() }, modifier) {
            SignedInRoutes(profile, session, auth, modifier)
        }
    } else {
        val request = accountRequest
        if (request == null) AuthRoute(modifier = modifier, viewModel = auth,
            onRegistered = accounts::register,
            onAuthenticated = { name, email -> accountRequest = listOf(name, email) })
        else AccountEntry(request[0], request[1], RepositoryModule.isStoreOwner(context, request[1]),
            remember(context) { RepositoryModule.staffAccessRepository(context) }, accounts,
            onResolved = { login, role -> session.signIn(login, role); accountRequest = null },
            onCancel = { accountRequest = null; auth.reset() }, modifier = modifier)
    }
}

@Composable
private fun SignedInRoutes(profile: CustomerProfile, session: SessionViewModel, auth: AuthViewModel, modifier: Modifier) {
    val context = LocalContext.current
    val accounts = remember(context) { RepositoryModule.accountSessionRepository(context) }
    val destination = RoleNavigation.destinationFor(session.role)
    if (destination == Destinations.CUSTOMER) {
        val discovery: DiscoveryViewModel = viewModel(factory = RepositoryModule.discoveryViewModelFactory)
        val account = profile.email.trim().lowercase(Locale.ROOT)
        val bookingFactory = remember(account) { RepositoryModule.bookingViewModelFactory(account) }
        val booking: BookingViewModel = viewModel(key = "customer.orders:$account", factory = bookingFactory)
        val complaintFactory = remember(account) { RepositoryModule.complaintViewModelFactory(account) }
        val complaints: ComplaintViewModel = viewModel(key = "customer.complaints:$account", factory = complaintFactory)
        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(booking, complaints, lifecycleOwner) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                booking.restoreHistory()
                complaints.restore()
                discovery.refreshPublicStores()
                var tick = 0
                while (true) {
                    booking.refreshTime()
                    if (++tick % 5 == 0) { booking.restoreHistory(); complaints.refreshCases(); discovery.refreshPublicStores() }
                    delay(1_000)
                }
            }
        }
        val locationSource = remember(context) { RepositoryModule.locationSource(context) }
        DisposableEffect(discovery, booking, complaints) {
            onDispose { discovery.clear(); booking.clear(); complaints.clear() }
        }
        CustomerRoute(profile, { name, phone ->
            val updated = accounts.updateProfile(profile.copy(name = name, phone = phone), session.accessVersion)
            if (session.profile?.email == updated.email) session.updateProfile(updated.name, updated.phone) else false
        },
            onLogout = { discovery.clear(); booking.clear(); complaints.clear(); auth.reset(); session.signOut() },
            vm = discovery, booking = booking, complaints = complaints, locationSource = locationSource, modifier = modifier,
            onPartner = session::switchToOwner, isPartner = RepositoryModule.isStoreOwner(context, account),
            onStaff = if (session.isStaffIdentity) session::switchToStaff else null)
    } else if (destination == Destinations.OWNER) {
        val account = profile.email.trim().lowercase(Locale.ROOT)
        val factory = remember(account) { RepositoryModule.storeViewModelFactory(account) }
        val store: StoreManagementViewModel = viewModel(key = "owner.store:$account", factory = factory)
        val menuFactory = remember(account) { RepositoryModule.menuViewModelFactory(account) }
        val menu: MenuViewModel = viewModel(key = "owner.menu:$account", factory = menuFactory)
        val boxesFactory = remember(account) { RepositoryModule.ownerBoxesViewModelFactory(account) }
        val boxes: OwnerBoxesViewModel = viewModel(key = "owner.boxes:$account", factory = boxesFactory)
        val ordersFactory = remember(account) { RepositoryModule.ownerOrdersViewModelFactory(account) }
        val orders: OwnerOrdersViewModel = viewModel(key = "owner.orders:$account", factory = ordersFactory)
        val employeesFactory = remember(account) { RepositoryModule.employeesViewModelFactory(account) }
        val employees: EmployeeViewModel = viewModel(key = "owner.employees:$account", factory = employeesFactory)
        val revenueFactory = remember(account) { RepositoryModule.revenueViewModelFactory(account) }
        val revenue: RevenueViewModel = viewModel(key = "owner.revenue:$account", factory = revenueFactory)
        val ownerLifecycle = LocalLifecycleOwner.current
        LaunchedEffect(boxes, orders, ownerLifecycle) {
            ownerLifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                boxes.load()
                orders.refresh()
                var tick = 0
                while (true) {
                    boxes.refreshTime(); orders.tick()
                    if (++tick % 5 == 0) { orders.refresh(); boxes.refreshStock() }
                    delay(1_000)
                }
            }
        }
        DisposableEffect(store, menu, boxes, orders, employees, revenue) {
            onDispose { store.clearTransient(); menu.clearTransient(); boxes.clearTransient(); orders.clear(); employees.clear(); revenue.clear() }
        }
        OwnerRoute(account, store,
            locationSource = remember(context) { RepositoryModule.locationSource(context) },
            onCustomer = { store.clearTransient(); menu.clearTransient(); boxes.clearTransient(); orders.clear(); employees.clear(); revenue.clear(); session.switchToCustomer() },
            onLogout = { store.clearTransient(); menu.clearTransient(); boxes.clearTransient(); orders.clear(); employees.clear(); revenue.clear(); auth.reset(); session.signOut() }, modifier = modifier,
            menuContent = { name, back -> MenuRoute(name, menu, back) },
            boxesContent = { name, back, onMenu -> OwnerBoxesRoute(name, boxes, back, onMenu) },
            boxesFullscreen = boxes.state.page != OwnerBoxPage.LIST,
            ordersContent = { name -> OwnerOrdersRoute(name, orders) },
            ordersFullscreen = orders.state.page != OwnerOrderPage.LIST,
            employeesContent = { name, back -> EmployeeRoute(name, employees, back) },
            revenueContent = { name, payout -> RevenueRoute(name, revenue, store.state.banks, payout) },
            revenueFullscreen = revenue.state.page != RevenuePage.OVERVIEW,
            summary = OwnerOverviewUiState(sold = orders.state.paidToday.sumOf { it.quantity },
                available = boxes.state.available, awaitingPickup = orders.state.count(OwnerOrderFilter.AWAITING_PICKUP),
                revenue = orders.state.paidToday.sumOf { it.total }))
    } else if (destination == Destinations.ADMIN) {
        val account = profile.email.trim().lowercase(Locale.ROOT)
        val factory = remember(account) { RepositoryModule.storeApplicationsViewModelFactory(account) }
        val applications: StoreApplicationsViewModel =
            viewModel(key = "admin.applications:$account", factory = factory)
        val complaintsFactory = remember(account) { RepositoryModule.adminComplaintsViewModelFactory(account) }
        val complaints: AdminComplaintsViewModel = viewModel(key = "admin.complaints:$account", factory = complaintsFactory)
        val refundsFactory = remember(account) { RepositoryModule.adminRefundsViewModelFactory(account) }
        val refunds: com.foodrescue.app.feature.refund.presentation.AdminRefundsViewModel =
            viewModel(key = "admin.refunds:$account", factory = refundsFactory)
        val supervisionFactory = remember(account) { RepositoryModule.storeSupervisionViewModelFactory(account) }
        val supervision: com.foodrescue.app.feature.store.presentation.StoreSupervisionViewModel =
            viewModel(key = "admin.supervision:$account", factory = supervisionFactory)
        val usersFactory = remember(account) { RepositoryModule.adminUsersViewModelFactory(account) }
        val users: AdminUsersViewModel = viewModel(key = "admin.users:$account", factory = usersFactory)
        val financeFactory = remember(account) { RepositoryModule.adminFinanceViewModelFactory(account) }
        val finance: com.foodrescue.app.feature.finance.presentation.AdminFinanceViewModel =
            viewModel(key = "admin.finance:$account", factory = financeFactory)
        val policyFactory = remember(account) { RepositoryModule.financialPolicyViewModelFactory(account) }
        val policy: com.foodrescue.app.feature.finance.presentation.FinancialPolicyViewModel =
            viewModel(key = "admin.policy:$account", factory = policyFactory)
        DisposableEffect(applications, complaints, refunds, supervision, users, finance, policy) {
            onDispose { applications.clear(); complaints.clear(); refunds.clear(); supervision.clear(); users.clear(); finance.clear(); policy.clear() }
        }
        AdminRoute(account, applications, complaints,
            { applications.clear(); complaints.clear(); refunds.clear(); supervision.clear(); users.clear(); finance.clear(); policy.clear(); auth.reset(); session.signOut() },
            modifier, refunds, supervision, users, finance, policy)
    } else if (destination == Destinations.STAFF) {
        val account = profile.email.trim().lowercase(Locale.ROOT)
        val factory = remember(account) { RepositoryModule.staffPackingViewModelFactory(account) }
        val staff: StaffPackingViewModel = viewModel(key = "staff.packing:$account", factory = factory)
        val handoverFactory = remember(account) { RepositoryModule.staffHandoverViewModelFactory(account) }
        val handover: StaffHandoverViewModel = viewModel(key = "staff.handover:$account", factory = handoverFactory)
        val conditionFactory = remember(account) { RepositoryModule.staffBoxConditionViewModelFactory(account) }
        val condition: StaffBoxConditionViewModel = viewModel(key = "staff.condition:$account", factory = conditionFactory)
        val noShowFactory = remember(account) { RepositoryModule.staffNoShowViewModelFactory(account) }
        val noShow: StaffNoShowViewModel = viewModel(key = "staff.noShow:$account", factory = noShowFactory)
        DisposableEffect(staff, handover, condition, noShow) {
            onDispose { staff.clear(); handover.clear(); condition.clear(); noShow.clear() }
        }
        StaffRoute(staff, handover, condition, noShow, { staff.clear(); handover.clear(); condition.clear(); noShow.clear(); session.switchToCustomer() },
            { staff.clear(); handover.clear(); condition.clear(); noShow.clear(); auth.reset(); session.signOut() }, modifier)
    }
}
