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
import com.foodrescue.app.di.RepositoryModule
import com.foodrescue.app.feature.auth.presentation.AuthRoute
import com.foodrescue.app.feature.auth.presentation.AuthViewModel
import com.foodrescue.app.feature.discovery.presentation.DiscoveryViewModel
import com.foodrescue.app.feature.order.presentation.BookingViewModel
import com.foodrescue.app.feature.complaint.presentation.ComplaintViewModel

@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val session: SessionViewModel = viewModel()
    val auth: AuthViewModel = viewModel()
    val profile = session.profile
    val destination = RoleNavigation.destinationFor(session.role)
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
                while (true) {
                    booking.refreshTime()
                    delay(1_000)
                }
            }
        }
        val context = LocalContext.current
        val locationSource = remember(context) { RepositoryModule.locationSource(context) }
        CustomerRoute(profile, session::updateProfile,
            onLogout = { discovery.clear(); booking.clear(); complaints.clear(); auth.reset(); session.signOut() },
            vm = discovery, booking = booking, complaints = complaints, locationSource = locationSource, modifier = modifier)
    } else {
        AuthRoute(modifier = modifier, viewModel = auth, onAuthenticated = session::signInCustomer)
    }
}
