package com.foodrescue.app.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
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

@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val session: SessionViewModel = viewModel()
    val auth: AuthViewModel = viewModel()
    val profile = session.profile
    val destination = RoleNavigation.destinationFor(session.role)
    val context = LocalContext.current
    if (destination == Destinations.CUSTOMER && profile != null) {
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
                    if (++tick % 5 == 0) { booking.restoreHistory(); discovery.refreshPublicStores() }
                    delay(1_000)
                }
            }
        }
        val locationSource = remember(context) { RepositoryModule.locationSource(context) }
        CustomerRoute(profile, session::updateProfile,
            onLogout = { discovery.clear(); booking.clear(); complaints.clear(); auth.reset(); session.signOut() },
            vm = discovery, booking = booking, complaints = complaints, locationSource = locationSource, modifier = modifier,
            onPartner = session::switchToOwner, isPartner = RepositoryModule.isStoreOwner(context, account))
    } else if (destination == Destinations.OWNER && profile != null) {
        val account = profile.email.trim().lowercase(Locale.ROOT)
        val factory = remember(account) { RepositoryModule.storeViewModelFactory(account) }
        val store: StoreManagementViewModel = viewModel(key = "owner.store:$account", factory = factory)
        val menuFactory = remember(account) { RepositoryModule.menuViewModelFactory(account) }
        val menu: MenuViewModel = viewModel(key = "owner.menu:$account", factory = menuFactory)
        val boxesFactory = remember(account) { RepositoryModule.ownerBoxesViewModelFactory(account) }
        val boxes: OwnerBoxesViewModel = viewModel(key = "owner.boxes:$account", factory = boxesFactory)
        val ordersFactory = remember(account) { RepositoryModule.ownerOrdersViewModelFactory(account) }
        val orders: OwnerOrdersViewModel = viewModel(key = "owner.orders:$account", factory = ordersFactory)
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
        OwnerRoute(account, store,
            locationSource = remember(context) { RepositoryModule.locationSource(context) },
            onCustomer = { store.clearTransient(); menu.clearTransient(); boxes.clearTransient(); orders.clear(); session.switchToCustomer() },
            onLogout = { store.clearTransient(); menu.clearTransient(); boxes.clearTransient(); orders.clear(); auth.reset(); session.signOut() }, modifier = modifier,
            menuContent = { name, back -> MenuRoute(name, menu, back) },
            boxesContent = { name, back, onMenu -> OwnerBoxesRoute(name, boxes, back, onMenu) },
            boxesFullscreen = boxes.state.page != OwnerBoxPage.LIST,
            ordersContent = { name -> OwnerOrdersRoute(name, orders) },
            ordersFullscreen = orders.state.page != OwnerOrderPage.LIST,
            summary = OwnerOverviewUiState(sold = orders.state.paidToday.sumOf { it.quantity },
                available = boxes.state.available, awaitingPickup = orders.state.count(OwnerOrderFilter.AWAITING_PICKUP),
                revenue = orders.state.paidToday.sumOf { it.total }))
    } else {
        AuthRoute(modifier = modifier, viewModel = auth, onAuthenticated = { name, email ->
            if (RepositoryModule.isStoreOwner(context, email)) session.signInOwner(name, email)
            else session.signInCustomer(name, email)
        })
    }
}
