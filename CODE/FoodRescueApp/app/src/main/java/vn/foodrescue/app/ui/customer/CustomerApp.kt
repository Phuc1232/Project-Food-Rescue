package vn.foodrescue.app.ui.customer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    var modal by remember { mutableStateOf<String?>(null) }
    val vm: DiscoveryViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler(preview && (boxId != null || tab != 0)) {
        if (boxId != null) boxId = null else tab = 0
    }
    if (!preview) {
        AuthFlow(onPreview = if (BuildConfig.DEBUG) ({ preview = true }) else null)
        return
    }
    val selected = demoBoxes.find { it.id == boxId }
    val colors = MaterialTheme.colorScheme
    Scaffold(containerColor = colors.background, bottomBar = {
        if (selected == null) {
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
        } else Surface(color = colors.surface, shadowElevation = 4.dp) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Giá mỗi hộp", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    Text(money(selected.price), color = colors.primary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
                Button(onClick = { modal = "booking" }, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)) { Text("Đặt hộp") }
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if (selected != null) BoxDetail(selected) { boxId = null }
            else key(tab) {
                when (tab) {
                    0, 1 -> DiscoveryScreen(state, tab == 1, vm::search, { tab = 1 },
                        { boxId = it }, vm::reset)
                    2 -> CatalogEmpty("Đơn hàng của bạn", "Bản xem thử chưa có đơn hàng. Bạn có thể khám phá các hộp mẫu trước.", "Khám phá", { tab = 0 })
                    3 -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text("Tài khoản", style = MaterialTheme.typography.headlineLarge)
                        Text("Bạn đang xem thử giao diện. Chưa có phiên đăng nhập thật.", color = colors.onSurfaceVariant)
                        OutlinedButton(onClick = { preview = false; tab = 0; boxId = null; vm.reset() }) { Text("Về màn hình chào") }
                    }
                }
            }
        }
    }
    if (modal == "booking") AlertDialog(onDismissRequest = { modal = null }, title = { Text("Hộp minh họa") },
        text = { Text("Bạn đang xem thử giao diện. Chức năng tạo đơn và thanh toán sẽ được triển khai ở bước tiếp theo.") },
        confirmButton = { TextButton(onClick = { modal = null }) { Text("Đã hiểu") } })
}

@Composable
private fun BoxDetail(box: DemoBox, onBack: () -> Unit) {
    val c = MaterialTheme.colorScheme
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
                    Column { Text("Nhận tại cửa hàng", fontWeight = FontWeight.SemiBold);Text("Hôm nay · ${box.pickup}", style = MaterialTheme.typography.bodyMedium) }
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
}

@Preview(name = "Khám phá · compact", widthDp = 360, heightDp = 800, showBackground = true)
@Preview(name = "Khám phá · large text", widthDp = 360, heightDp = 800, fontScale = 1.5f, showBackground = true)
@Composable
private fun DiscoveryPreview() { FoodRescueTheme {
    DiscoveryScreen(DiscoveryState(), false, {}, {}, {}, {})
} }
