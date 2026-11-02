package vn.foodrescue.app.ui.customer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
internal fun StoreMapScreen(
    state: DiscoveryState, selectedId: Int?, viewport: List<Double>,
    onSelect: (Int) -> Unit, onViewport: (List<Double>) -> Unit,
    onBack: () -> Unit, onFilters: () -> Unit, onArea: () -> Unit,
    onBox: (Int) -> Unit, onReset: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val density = LocalDensity.current
    val offers = remember(state.boxes) { mapOffers(state.boxes) }
    val selected = selectedMapOffer(offers, selectedId)
    var load by remember { mutableStateOf(MapLoadState.LOADING) }
    var retry by remember { mutableIntStateOf(0) }
    var fit by remember { mutableIntStateOf(0) }
    var topInset by remember { mutableIntStateOf(with(density) { 112.dp.roundToPx() }) }
    var bottomInset by remember { mutableIntStateOf(with(density) { 320.dp.roundToPx() }) }
    var directions by remember { mutableStateOf<StoreLocation?>(null) }

    LaunchedEffect(retry) {
        delay(20_000)
        if (load == MapLoadState.LOADING) load = MapLoadState.ERROR
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.surfaceContainerLow)) {
        val cardMaxHeight = maxHeight * .52f
        NativeStoreMap(offers, selected?.id, fit, retry, topInset, bottomInset, viewport,
            onSelect, onViewport, { load = it }, Modifier.fillMaxSize())

        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()
            .background(Brush.verticalGradient(listOf(colors.background.copy(alpha = .96f), Color.Transparent)))
            .onSizeChanged { topInset = it.height }
            .padding(horizontal = 16.dp).padding(top = 10.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(color = colors.surface, shape = RoundedCornerShape(22.dp), shadowElevation = 5.dp) {
                Row(Modifier.fillMaxWidth().padding(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        CatalogGlyph(CatalogIcon.BACK, tint = colors.primary, description = "Về danh sách hộp")
                    }
                    Surface(onClick = onArea, modifier = Modifier.weight(1f), color = Color.Transparent, shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                            Text("Bản đồ cửa hàng", fontSize = 11.sp, lineHeight = 16.sp, color = colors.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(state.district, Modifier.weight(1f, fill = false), fontSize = 15.sp, lineHeight = 21.sp,
                                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                CatalogGlyph(CatalogIcon.CHEVRON, Modifier.size(16.dp))
                            }
                        }
                    }
                    IconButton(onClick = onFilters) {
                        BadgedBox(badge = { if (state.filters.activeCount > 0) Badge(containerColor = colors.primary) { Text(state.filters.activeCount.toString()) } }) {
                            CatalogGlyph(CatalogIcon.FILTER, tint = colors.primary, description = "Bộ lọc bản đồ")
                        }
                    }
                }
            }
            Surface(color = colors.surface.copy(alpha = .96f), shape = CircleShape, shadowElevation = 1.dp) {
                Text(buildString {
                    append("${offers.size} nơi có hộp")
                    if (offers.any { it.location?.isPreview == true }) append(" · Địa điểm mẫu")
                    if (state.query.isNotBlank()) append(" · “${state.query}”")
                }, Modifier.padding(horizontal = 13.dp, vertical = 7.dp), fontSize = 10.sp, lineHeight = 15.sp,
                    color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }

        if (load != MapLoadState.READY) Surface(
            modifier = Modifier.align(Alignment.Center).offset(y = (-45).dp).padding(horizontal = 36.dp),
            shape = RoundedCornerShape(20.dp), color = colors.surface, shadowElevation = 4.dp,
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (load == MapLoadState.LOADING) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text("Đang tải bản đồ…", fontSize = 13.sp)
                } else {
                    Text("Chưa tải được bản đồ", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Kiểm tra kết nối mạng rồi thử lại.", fontSize = 12.sp, color = colors.onSurfaceVariant)
                    TextButton(onClick = { load = MapLoadState.LOADING; retry++ }) { Text("Thử lại") }
                }
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .onSizeChanged { bottomInset = it.height }
            .padding(horizontal = 16.dp).padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Surface(onClick = { fit++ }, shape = CircleShape, color = colors.surface, shadowElevation = 3.dp) {
                    Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                        CatalogGlyph(CatalogIcon.COMPASS, tint = colors.primary, description = "Xem tất cả địa điểm")
                    }
                }
            }
            Surface(shape = RoundedCornerShape(24.dp), color = colors.surface, shadowElevation = 10.dp,
                border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = .6f))) {
                Column(Modifier.heightIn(max = cardMaxHeight).verticalScroll(rememberScrollState())) {
                    if (selected == null) {
                        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(if (state.boxes.isEmpty()) "Chưa có hộp phù hợp" else "Chưa có vị trí cửa hàng", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text(if (state.boxes.isEmpty()) "Thử đổi khu vực hoặc nới rộng bộ lọc để xem thêm địa điểm." else "Bạn vẫn có thể xem thông tin hộp trong danh sách.",
                                fontSize = 13.sp, lineHeight = 19.sp, color = colors.onSurfaceVariant)
                            Button(onClick = if (state.boxes.isEmpty()) onReset else onBack, modifier = Modifier.fillMaxWidth()) {
                                Text(if (state.boxes.isEmpty()) "Xóa tìm kiếm và bộ lọc" else "Về danh sách", fontSize = 12.sp)
                            }
                        }
                    } else {
                        val index = offers.indexOfFirst { it.id == selected.id }
                        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("ĐỊA ĐIỂM ${index + 1} / ${offers.size}", Modifier.weight(1f), fontSize = 9.sp, lineHeight = 13.sp,
                                letterSpacing = .8.sp, fontWeight = FontWeight.Medium, color = colors.onSurfaceVariant)
                            IconButton(onClick = { onSelect(offers[index - 1].id) }, enabled = index > 0) {
                                CatalogGlyph(CatalogIcon.BACK, Modifier.size(19.dp), description = "Cửa hàng trước")
                            }
                            IconButton(onClick = { onSelect(offers[index + 1].id) }, enabled = index < offers.lastIndex) {
                                CatalogGlyph(CatalogIcon.BACK, Modifier.size(19.dp).rotate(180f), description = "Cửa hàng tiếp theo")
                            }
                        }
                        AnimatedContent(targetState = selected, label = "store-card",
                            transitionSpec = { (fadeIn(tween(200)) + slideInHorizontally(tween(200)) { it / 12 }) togetherWith fadeOut(tween(90)) }) { box ->
                            MapStoreCard(box, { onBox(box.id) }) {
                                box.location?.let { location ->
                                    if (location.isPreview) directions = location else openDirections(context, location)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    directions?.let { location ->
        PreviewDirectionsDialog(onOpen = { directions = null; openDirections(context, location) }, onDismiss = { directions = null })
    }
}

@Composable
private fun MapStoreCard(box: DemoBox, onBox: () -> Unit, onDirections: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(box.category.label, fontSize = 10.sp, lineHeight = 15.sp, color = colors.primary)
                Text(box.store, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-.35).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Image(painterResource(box.photo()), "Ảnh minh họa ${box.category.label.lowercase()}",
                Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
        }
        Text(box.location?.address.orEmpty(), fontSize = 11.sp, lineHeight = 16.sp, color = colors.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CatalogGlyph(CatalogIcon.CLOCK, Modifier.size(16.dp), colors.onSurfaceVariant)
            Text(box.pickup, Modifier.weight(1f), fontSize = 11.sp, lineHeight = 17.sp, color = colors.onSurfaceVariant)
            Text(money(box.price), fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, color = colors.primary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onBox, modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)) {
                Text("Xem hộp", fontSize = 12.sp, maxLines = 1)
            }
            OutlinedButton(onClick = onDirections, modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, colors.outlineVariant), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)) {
                CatalogGlyph(CatalogIcon.ARROW, Modifier.size(15.dp).rotate(-40f), colors.primary)
                Spacer(Modifier.width(5.dp))
                Text("Chỉ đường", fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}
