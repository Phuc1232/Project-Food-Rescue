package com.foodrescue.app.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.foodrescue.app.app.session.SessionViewModel
import com.foodrescue.app.dashboard.customer.CustomerRoute
import com.foodrescue.app.di.RepositoryModule
import com.foodrescue.app.feature.auth.presentation.AuthRoute
import com.foodrescue.app.feature.auth.presentation.AuthViewModel
import com.foodrescue.app.feature.discovery.presentation.DiscoveryViewModel

@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val session: SessionViewModel = viewModel()
    val auth: AuthViewModel = viewModel()
    val profile = session.profile
    val destination = RoleNavigation.destinationFor(session.role)
    if (destination == Destinations.CUSTOMER && profile != null) {
        val discovery: DiscoveryViewModel = viewModel(factory = RepositoryModule.discoveryViewModelFactory)
        val context = LocalContext.current
        val locationSource = remember(context) { RepositoryModule.locationSource(context) }
        CustomerRoute(profile, session::updateProfile,
            onLogout = { discovery.clear(); auth.reset(); session.signOut() },
            vm = discovery, locationSource = locationSource, modifier = modifier)
    } else {
        AuthRoute(modifier = modifier, viewModel = auth, onAuthenticated = session::signInCustomer)
    }
}
