package vn.foodrescue.app.ui.customer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import vn.foodrescue.app.BuildConfig
import vn.foodrescue.app.ui.auth.AuthFlow
import vn.foodrescue.app.ui.theme.FoodRescueTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerApp(startInPreview: Boolean = false) {
    var preview by rememberSaveable { mutableStateOf(BuildConfig.DEBUG && startInPreview) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var boxId by rememberSaveable { mutableStateOf<Int?>(null) }
    var mapOpen by rememberSaveable { mutableStateOf(false) }
    var mapSelectedId by rememberSaveable { mutableStateOf<Int?>(null) }
    var mapViewport by rememberSaveable { mutableStateOf(emptyList<Double>()) }
    var modal by remember { mutableStateOf<String?>(null) }
    var route by rememberSaveable { mutableStateOf("home") }
    var orderId by rememberSaveable { mutableStateOf<String?>(null) }
    val vm: DiscoveryViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler(preview && route == "home" && (boxId != null || mapOpen || tab != 0)) {
        if (boxId != null) boxId = null else if (mapOpen) mapOpen = false else tab = 0
    }
    if (!BuildConfig.DEBUG || !preview) {
        AuthFlow(onPreview = if (BuildConfig.DEBUG) ({ preview = true }) else null)
        return
    }
    val customerVm: CustomerViewModel = viewModel()
    val customer by customerVm.state.collectAsStateWithLifecycle()
    val now by customerVm.now.collectAsStateWithLifecycle()
    val error by customerVm.error.collectAsStateWithLifecycle()
    LaunchedEffect(customer.orders, now / 60_000) {
        vm.stock(demoBoxes.associate { it.id to customerVm.available(it, previewPickup(it, now).first) })
    }
    val selected = state.catalog.find { it.id == boxId }
    val selectedOrder = customer.orders.find { it.id == orderId }
    val toOrders: () -> Unit = { route = "home"; tab = 2; boxId = null; mapOpen = false; orderId = null }
    val routeBack: () -> Unit = {
        when (route) {
            "checkout", "profile" -> route = "home"
            "payment", "cancel", "complaint" -> route = "order"
            else -> toOrders()
        }
    }
    BackHandler(route != "home", routeBack)
    error?.let { text -> AlertDialog(onDismissRequest = customerVm::dismissError, title = { Text("Chưa thực hiện được") },
        text = { Text(text) }, confirmButton = { TextButton(onClick = customerVm::dismissError) { Text("Đã hiểu") } }) }
    when {
        route == "checkout" && selected != null -> {
            CheckoutScreen(selected, customerVm, routeBack) { orderId = it; route = "payment" }; return
        }
        route == "profile" -> {
            EditProfileScreen(customer.profile, routeBack) { name, phone -> if (customerVm.saveProfile(name, phone)) route = "home" }; return
        }
        selectedOrder != null && route != "home" -> {
            when (route) {
                "payment" -> PaymentScreen(selectedOrder, now, customerVm, routeBack) { route = "order" }
                "cancel" -> CancelOrderScreen(selectedOrder, now, routeBack) { if (customerVm.cancel(selectedOrder.id, it)) route = "order" }
                "complaint" -> ComplaintScreen(selectedOrder, now, routeBack) { reason, text, evidence ->
                    if (customerVm.complain(selectedOrder.id, reason, text, evidence)) route = "order"
                }
                else -> OrderDetailScreen(selectedOrder, now, customerVm, toOrders,
                    { route = "payment" }, { route = "cancel" }, { route = "complaint" })
            }
            return
        }
    }
    val colors = MaterialTheme.colorScheme
    Scaffold(containerColor = colors.background, bottomBar = {
        if (selected == null && !mapOpen) {
            Surface(color = colors.surface, shadowElevation = 2.dp) {
                NavigationBar(containerColor = colors.surface, tonalElevation = 0.dp) {
                    val labels = listOf("Khám phá", "Tìm kiếm", "Đơn hàng", "Tài khoản")
                    val icons = listOf(CatalogIcon.COMPASS, CatalogIcon.SEARCH, CatalogIcon.RECEIPT, CatalogIcon.PERSON)
                    labels.forEachIndexed { index, label ->
                        NavigationBarItem(selected = tab == index, onClick = { tab = index },
                            icon = { CatalogGlyph(icons[index]) }, label = { Text(label, fontSize = 11.sp, lineHeight = 16.sp, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = colors.primary,
                                selectedTextColor = colors.primary, indicatorColor = colors.primaryContainer,
                                unselectedIconColor = colors.onSurfaceVariant, unselectedTextColor = colors.onSurfaceVariant))
                    }
                }
            }
        } else if (selected != null) Surface(color = colors.surface, shadowElevation = 4.dp) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Giá mỗi hộp", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    Text(money(selected.price), color = colors.primary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
                Button(onClick = { route = "checkout" }, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)) { Text("Đặt hộp") }
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if (selected != null) BoxDetail(selected) { boxId = null }
            else if (mapOpen) StoreMapScreen(state, mapSelectedId, mapViewport,
                { mapSelectedId = it }, { mapViewport = it }, { mapOpen = false },
                { modal = "filters" }, { modal = "area" }, { boxId = it }, vm::reset)
            else key(tab) {
                when (tab) {
                    0, 1 -> DiscoveryScreen(state, tab == 1, vm::search, { tab = 1 },
                        { modal = "filters" }, { modal = "area" }, { mapOpen = true }, { boxId = it }, vm::reset)
                    2 -> OrdersScreen(customer.orders, { orderId = it; route = "order" }, { tab = 0 }, customerVm::addExamples)
                    3 -> ProfileScreen(customer.profile, { route = "profile" }, { tab = 2 }) {
                        customerVm.clearPreview(); preview = false; tab = 0; boxId = null; orderId = null
                        mapOpen = false; mapSelectedId = null; mapViewport = emptyList(); vm.clear()
                    }
                }
            }
        }
    }
    if (modal != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { modal = null }, sheetState = sheetState, containerColor = colors.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
            when (modal) {
                "filters" -> FilterSheet(state) { vm.filters(it); modal = null }
                "area" -> CustomerAreaSheet(state, { vm.district(it); mapViewport = emptyList(); modal = null },
                    { vm.location(it); mapViewport = emptyList(); modal = null })
            }
        }
    }
}

@Composable
private fun FilterSheet(state: DiscoveryState, onApply: (DiscoveryFilters) -> Unit) {
    var draft by remember { mutableStateOf(state.filters) }
    val priceCeiling = remember(state.filters.price) { maxOf(catalogPriceCeiling(), state.filters.price?.max ?: 0) }
    Column(Modifier.fillMaxWidth().fillMaxHeight(.94f)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Bộ lọc", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
                TextButton(onClick = { draft = DiscoveryFilters() }) { Text("Đặt lại") }
            }
            Text("Mức giá mỗi hộp", style = MaterialTheme.typography.titleSmall)
            PriceRangePicker(draft.price, priceCeiling) { draft = draft.copy(price = it) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("Loại hộp", style = MaterialTheme.typography.titleSmall)
            ChoiceRow("Tất cả loại hộp", draft.category == null) { draft = draft.copy(category = null) }
            BoxCategory.entries.forEach { option -> ChoiceRow(option.label, draft.category == option) { draft = draft.copy(category = option) } }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("Khung giờ nhận", style = MaterialTheme.typography.titleSmall)
            PickupBand.entries.forEach { option -> ChoiceRow(option.label, draft.pickup == option) { draft = draft.copy(pickup = option) } }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("Sắp xếp theo", style = MaterialTheme.typography.titleSmall)
            CatalogSort.entries.forEach { option -> ChoiceRow(option.label, draft.sort == option) { draft = draft.copy(sort = option) } }
            Spacer(Modifier.height(12.dp))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Button(onClick = { onApply(draft) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).navigationBarsPadding().heightIn(min = 54.dp), shape = RoundedCornerShape(14.dp)) {
            Text("Xem ${filterCatalog(state.query, state.district, draft, state.catalog).size} hộp")
        }
    }
}

@Composable
private fun PriceRangePicker(selected: PriceRange?, ceiling: Int, onChange: (PriceRange?) -> Unit) {
    val low = selected?.min ?: 0
    val high = selected?.max ?: ceiling
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("Từ" to low, "Đến" to high).forEach { (label, amount) ->
                Surface(modifier = Modifier.weight(1f), color = colors.surfaceContainerLow,
                    border = BorderStroke(1.dp, colors.outlineVariant), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        Text(money(amount), fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, color = colors.primary)
                    }
                }
            }
        }
        RangeSlider(value = low.toFloat()..high.toFloat(),
            onValueChange = { onChange(selectedPriceRange(it.start, it.endInclusive, ceiling)) },
            valueRange = 0f..ceiling.toFloat(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = "Khoảng giá mỗi hộp" },
            colors = SliderDefaults.colors(thumbColor = colors.primary, activeTrackColor = colors.primary,
                inactiveTrackColor = colors.primaryContainer))
        Text("Kéo hai đầu để chọn khoảng giá.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        RadioButton(selected = selected, onClick = null)
    }
}

@Composable
private fun BoxDetail(box: DemoBox, onBack: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    var directions by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item { Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { CatalogGlyph(CatalogIcon.BACK, description = "Quay lại khám phá") }
            Text("Thông tin hộp", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        } }
        item { CatalogPhoto(box, Modifier.fillMaxWidth().height(230.dp), showCount = true) }
        item { Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StoreMonogram(box)
                Column { Text(box.store, style = MaterialTheme.typography.titleMedium);Text("${box.district} · ${distance(box.distanceMeters)}", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant) }
            }
            Text(box.name, style = MaterialTheme.typography.headlineMedium)
            Text(box.description, style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant)
            Surface(color = c.primaryContainer, shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CatalogGlyph(CatalogIcon.CLOCK, tint = c.primary)
                    Column { Text("Nhận tại cửa hàng", fontWeight = FontWeight.SemiBold);Text("${orderTime(previewPickup(box, System.currentTimeMillis()).first)} – ${box.pickup.substringAfter(" – ")}", style = MaterialTheme.typography.bodyMedium) }
                }
            }
            box.location?.let { location ->
                Text("Địa điểm nhận hộp", style = MaterialTheme.typography.titleSmall)
                Text(location.address, style = MaterialTheme.typography.bodyMedium)
                if (location.isPreview) Text("Địa điểm mẫu, chưa phải cửa hàng đã xác minh.", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                OutlinedButton(onClick = { if (location.isPreview) directions = true else openDirections(context, location) }) {
                    Text("Chỉ đường đến cửa hàng")
                }
            }
            Text("Giá trị thực phẩm tối thiểu", style = MaterialTheme.typography.titleSmall)
            Text("${money(box.minimumValue)} cho mỗi hộp. Thành phần và số lượng từng món không được công bố trước.", style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider(color = c.outlineVariant)
            Text("Thông tin dị ứng", style = MaterialTheme.typography.titleSmall)
            Text(box.allergens, style = MaterialTheme.typography.bodyMedium)
            Text("Bản xem thử · Ảnh không thể hiện chính xác các món sẽ có trong hộp.", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
        } }
    }
    if (directions) box.location?.let { location ->
        PreviewDirectionsDialog(onOpen = { directions = false; openDirections(context, location) }, onDismiss = { directions = false })
    }
}

@Preview(name = "Khám phá · compact", widthDp = 360, heightDp = 800, showBackground = true)
@Preview(name = "Khám phá · large text", widthDp = 360, heightDp = 800, fontScale = 1.5f, showBackground = true)
@Composable
private fun DiscoveryPreview() { FoodRescueTheme {
    DiscoveryScreen(DiscoveryState(), false, {}, {}, {}, {}, {}, {}, {})
} }
