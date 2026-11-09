package com.foodrescue.app.dashboard.customer

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.foodrescue.app.feature.account.domain.CustomerProfile
import com.foodrescue.app.feature.account.presentation.*
import com.foodrescue.app.core.designsystem.component.FoodRescueGlyph
import com.foodrescue.app.core.designsystem.component.FoodRescueIcon
import com.foodrescue.app.feature.discovery.domain.LocationSource
import com.foodrescue.app.feature.discovery.presentation.map.CatalogStoreMapScreen
import com.foodrescue.app.feature.store.presentation.StoreDetailScreen
import com.foodrescue.app.feature.surprisebox.presentation.SurpriseBoxDetailFooter
import com.foodrescue.app.feature.surprisebox.presentation.SurpriseBoxDetailScreen
import com.foodrescue.app.feature.order.presentation.*
import com.foodrescue.app.feature.order.domain.pickupCode
import com.foodrescue.app.feature.order.domain.OrderFilter
import com.foodrescue.app.feature.payment.presentation.PaymentScreen
import com.foodrescue.app.feature.payment.presentation.PaymentUiState
import com.foodrescue.app.feature.discovery.presentation.*

private val NavigationSaver = Saver<CustomerNavigation, ArrayList<String>>(
    save = { arrayListOf(it.tab.toString()).apply { addAll(it.path) } },
    restore = { CustomerNavigation(it.first().toInt(), it.drop(1)) },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomerRoute(
    profile: CustomerProfile,
    onProfile: (String, String) -> Boolean,
    onLogout: () -> Unit,
    vm: DiscoveryViewModel,
    booking: BookingViewModel,
    locationSource: LocationSource,
    modifier: Modifier = Modifier,
) {
    var navigation by rememberSaveable(stateSaver = NavigationSaver) { mutableStateOf(CustomerNavigation()) }
    var modal by rememberSaveable { mutableStateOf<String?>(null) }
    var mapSelection by rememberSaveable { mutableStateOf<Int?>(null) }
    var viewport by rememberSaveable { mutableStateOf(emptyList<Double>()) }
    val pages = rememberSaveableStateHolder()
    val state = vm.state
    val bookingState = booking.state
    val colors = MaterialTheme.colorScheme
    val keyboard = LocalSoftwareKeyboardController.current
    val window = LocalActivity.current?.window
    SideEffect {
        window?.let { WindowCompat.getInsetsController(it, it.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        } }
    }
    val back: () -> Unit = { navigation = navigation.back() }
    val open: (String) -> Unit = { keyboard?.hide(); navigation = navigation.open(it) }
    val sheet: (String) -> Unit = { keyboard?.hide(); modal = it }
    val boxId = navigation.current?.takeIf { it.startsWith("box:") }?.substringAfter(':')?.toIntOrNull()
    val storeId = navigation.current?.takeIf { it.startsWith("store:") }?.substringAfter(':')?.toIntOrNull()
    val selectedBox = state.catalog.find { it.id == boxId }
    val bookingId = navigation.current?.takeIf { it.startsWith("booking:") }?.substringAfter(':')?.toIntOrNull()
    val reservationId = navigation.current?.takeIf { it.startsWith("reservation:") }?.substringAfter(':')
    val reservation = bookingState.reservations.firstOrNull { it.id == reservationId }
    val paymentId = navigation.current?.takeIf { it.startsWith("payment:") }?.substringAfter(':')
    val paymentReservation = bookingState.reservations.firstOrNull { it.id == paymentId && it.cancelledAtMillis == null }
    val paymentBox = bookingState.catalog.firstOrNull { it.id == paymentReservation?.boxId }
    val showMap: (Int) -> Unit = { id ->
        val selectedStore = state.catalog.find { it.id == id }?.storeId
        mapSelection = state.mapPresentation.boxes.firstOrNull { it.storeId == selectedStore }?.id
        viewport = emptyList()
        open("map")
    }
    BackHandler(modal == null && (navigation.current != null || navigation.tab != 0), back)

    if (navigation.current == "profile") {
        EditCustomerProfileScreen(profile, back) { name, phone -> if (onProfile(name, phone)) back() }
    } else if (paymentId != null && paymentReservation?.payment != null && paymentBox != null) {
        PaymentScreen(PaymentUiState(paymentReservation.payment, paymentBox.store, paymentReservation.quantity,
            bookingDate(paymentReservation.pickup.startMillis) + "\n" + bookingHours(paymentReservation.pickup),
            paymentBox.location?.address ?: paymentBox.district, paymentReservation.remainingSeconds(bookingState.nowMillis),
            paymentReservation.pickupCode(bookingState.nowMillis)),
            back, onCheck = { booking.checkPayment(paymentId) },
            onOrders = {
                booking.selectFilter(OrderFilter.ALL)
                pages.removeState("tab:2")
                navigation = navigation.selectTab(2)
            },
            onRebook = {
                val held = booking.prepare(paymentReservation.boxId, reset = true)
                navigation = navigation.selectTab(2).open(if (held != null) "reservation:$held" else "booking:" + paymentReservation.boxId)
            }, modifier = modifier)
    } else if (bookingId != null) {
        BookingScreen(bookingState, back, booking::changeQuantity, booking::acceptPolicy,
            booking::chooseNextPickup, onConfirm = {
                booking.confirm()?.let { navigation = navigation.back().open("reservation:" + it) }
            }, modifier = modifier)
    } else if (reservationId != null) {
        ReservationScreen(reservation, bookingState.catalog.firstOrNull { it.id == reservation?.boxId },
            bookingState.nowMillis, back,
            onExplore = { navigation = navigation.selectTab(0) },
            onRelease = { booking.release(reservationId) },
            onRebook = {
                reservation?.let {
                    val held = booking.prepare(it.boxId, reset = true)
                    navigation = navigation.back().open(if (held != null) "reservation:$held" else "booking:" + it.boxId)
                }
            }, onPayment = {
                if (booking.openPayment(reservationId)) open("payment:" + reservationId)
            }, modifier = modifier)
    } else Scaffold(modifier = modifier.fillMaxSize().imePadding(), containerColor = colors.background, bottomBar = {
        if (navigation.current == null) {
            Surface(color = colors.surface, shadowElevation = 2.dp) {
                NavigationBar(containerColor = colors.surface, tonalElevation = 0.dp) {
                    val labels = listOf("Khám phá", "Tìm kiếm", "Đơn hàng", "Tài khoản")
                    val icons = listOf(FoodRescueIcon.COMPASS, FoodRescueIcon.SEARCH, FoodRescueIcon.RECEIPT, FoodRescueIcon.PERSON)
                    labels.forEachIndexed { index, label ->
                        NavigationBarItem(selected = navigation.tab == index, onClick = {
                            keyboard?.hide(); navigation = navigation.selectTab(index)
                        }, icon = { FoodRescueGlyph(icons[index]) },
                            label = { Text(label, fontSize = 11.sp, lineHeight = 16.sp, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = colors.primary,
                                selectedTextColor = colors.primary, indicatorColor = colors.primaryContainer))
                    }
                }
            }
        } else if (selectedBox != null) {
            SurpriseBoxDetailFooter(selectedBox, bookingState.activeFor(selectedBox.id) != null) {
                val held = booking.prepare(selectedBox.id)
                open(if (held != null) "reservation:" + held else "booking:" + selectedBox.id)
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            pages.SaveableStateProvider(navigation.current ?: "tab:${navigation.tab}") {
                when {
                    selectedBox != null -> SurpriseBoxDetailScreen(selectedBox, back, { showMap(selectedBox.id) },
                        { open("store:" + selectedBox.storeId) })
                    storeId != null -> StoreDetailScreen(state.catalog.filter { it.storeId == storeId }, back,
                        { open("box:$it") }, { state.catalog.firstOrNull { it.storeId == storeId }?.let { showMap(it.id) } })
                    navigation.current == "map" -> CatalogStoreMapScreen(state.mapPresentation, mapSelection, viewport,
                        { mapSelection = it }, { viewport = it }, back, { sheet("filters") }, { sheet("area") },
                        { id -> open("store:$id") }, vm::reset)
                    navigation.tab in 0..1 -> {
                        val presentation = if (navigation.tab == 0) state.presentation.copy(storeResults = false, total = state.boxes.size.toLong()) else state.presentation
                        CatalogDiscoveryScreen(presentation, navigation.tab == 1, vm::search,
                            { navigation = navigation.selectTab(1) }, { sheet("filters") }, { sheet("area") },
                            { open("map") }, { open("box:$it") }, vm::reset, vm::kind,
                            { open("store:$it") })
                    }
                    navigation.tab == 2 -> CustomerOrdersScreen(bookingState,
                        { open("reservation:" + it) }, { navigation = navigation.selectTab(0) }, booking::selectFilter)
                    else -> CustomerProfileScreen(profile, { open("profile") }, { navigation = navigation.selectTab(2) }, {
                        onLogout()
                    })
                }
            }
        }
    }
    if (modal != null) ModalBottomSheet(onDismissRequest = { modal = null },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        when (modal) {
            "filters" -> FilterSheet(state) { vm.filters(it); modal = null }
            "area" -> CustomerAreaSheet(state, locationSource,
                { vm.district(it); viewport = emptyList(); modal = null },
                { vm.location(it); viewport = emptyList(); modal = null },
                onClearLocation = { vm.clearLocation(); viewport = emptyList(); modal = null })
        }
    }
}
