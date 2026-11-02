package vn.foodrescue.app.ui.customer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.foodrescue.app.R

internal fun DemoBox.photo() = when (category) {
    BoxCategory.BAKERY -> R.drawable.welcome_bakery
    BoxCategory.MEALS -> R.drawable.discover_meal
    BoxCategory.PRODUCE, BoxCategory.MIXED -> R.drawable.discover_produce
    BoxCategory.DRINKS -> R.drawable.discover_drinks
}

@Composable
internal fun DiscoveryScreen(s: DiscoveryState, searchMode: Boolean, onSearch: (String) -> Unit,
    onOpenSearch: () -> Unit, onFilters: () -> Unit, onArea: () -> Unit,
    onMap: () -> Unit, onBox: (Int) -> Unit, onReset: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val largeText = LocalDensity.current.fontScale > 1.2f
    var storeResults by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(Modifier.padding(horizontal = 24.dp).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Surface(onClick = onArea, color = Color.Transparent, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CatalogGlyph(CatalogIcon.PIN, Modifier.size(20.dp), colors.primary)
                            Column(Modifier.weight(1f)) {
                                Text("KHU VỰC NHẬN HỘP", fontSize = 9.sp, lineHeight = 14.sp, letterSpacing = 1.sp, color = colors.onSurfaceVariant, fontWeight = FontWeight.Medium)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(s.district, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    CatalogGlyph(CatalogIcon.CHEVRON, Modifier.size(17.dp))
                                }
                            }
                        }
                    }
                    Surface(onClick = onMap, color = colors.surface, shape = CircleShape, border = BorderStroke(1.dp, colors.outlineVariant)) {
                        Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) { CatalogGlyph(CatalogIcon.MAP, tint = colors.primary, description = "Xem bản đồ") }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (searchMode) "Tìm kiếm" else "Khám phá", Modifier.semantics { heading() }, fontSize = if (largeText) 26.sp else 30.sp,
                        lineHeight = if (largeText) 32.sp else 37.sp, letterSpacing = (-1).sp, fontWeight = FontWeight.Bold)
                    if (!largeText) Text(if (searchMode) "Tìm hộp bất ngờ hoặc cửa hàng." else "Surprise Box quanh bạn.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (searchMode) {
                        OutlinedTextField(s.query, onSearch, modifier = Modifier.weight(1f), singleLine = true,
                            placeholder = { Text("Hộp, cửa hàng…", fontSize = 14.sp) },
                            leadingIcon = { CatalogGlyph(CatalogIcon.SEARCH, Modifier.size(20.dp)) },
                            trailingIcon = if (s.query.isNotEmpty()) ({ IconButton(onClick = { onSearch("") }) { CatalogGlyph(CatalogIcon.CLOSE, Modifier.size(18.dp), description = "Xóa từ khóa") } }) else null,
                            shape = RoundedCornerShape(14.dp), textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = colors.surface, focusedContainerColor = colors.surface,
                                unfocusedBorderColor = colors.outlineVariant))
                    } else {
                        Surface(onClick = onOpenSearch, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                            color = colors.surface, border = BorderStroke(1.dp, colors.outlineVariant)) {
                            Row(Modifier.heightIn(min = 54.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CatalogGlyph(CatalogIcon.SEARCH, Modifier.size(20.dp), colors.onSurfaceVariant)
                                Text("Hộp, cửa hàng…", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                            }
                        }
                    }
                    Surface(onClick = onFilters, color = if (s.filters.activeCount > 0) colors.primaryContainer else colors.surface,
                        shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, colors.outlineVariant)) {
                        Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
                            CatalogGlyph(CatalogIcon.FILTER, tint = colors.primary, description = "Bộ lọc")
                            if (s.filters.activeCount > 0) Badge(Modifier.align(Alignment.TopEnd).padding(4.dp), containerColor = colors.primary) { Text(s.filters.activeCount.toString()) }
                        }
                    }
                }
            }
        }
        if (searchMode) item {
            Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !storeResults, onClick = { storeResults = false }, label = { Text("Hộp bất ngờ") })
                FilterChip(selected = storeResults, onClick = { storeResults = true }, label = { Text("Cửa hàng") })
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (searchMode) "Kết quả tìm kiếm" else "Quanh bạn hôm nay", Modifier.weight(1f).semantics { heading() },
                    fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-.3).sp, fontWeight = FontWeight.SemiBold)
                Text("${s.boxes.size} ${if (searchMode && storeResults) "nơi" else "hộp"}", fontSize = 12.sp, color = colors.onSurfaceVariant)
            }
        }
        if (s.filters.activeCount > 0) item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(listOfNotNull(s.filters.category?.label,
                    s.filters.price?.let { "${money(it.min)} – ${money(it.max)}" },
                    s.filters.pickup.takeIf { it != PickupBand.ALL }?.label,
                    s.filters.sort.takeIf { it != CatalogSort.NEAREST }?.label).joinToString(" · "),
                    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                TextButton(onClick = onReset) { Text("Đặt lại") }
            }
        }
        if (s.boxes.isEmpty()) item {
            CatalogEmpty("Chưa tìm thấy hộp phù hợp", "Thử từ khóa khác, đổi khu vực hoặc điều chỉnh bộ lọc.", "Xóa tìm kiếm và bộ lọc", onReset)
        }
        items(s.boxes, key = { it.id }) { box ->
            if (searchMode && storeResults) StoreResult(box) { onBox(box.id) }
            else DiscoveryCard(box) { onBox(box.id) }
        }
        item {
            Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                CatalogGlyph(CatalogIcon.BAG, Modifier.size(18.dp), colors.primary)
                Text("Món cụ thể trong hộp được giữ bí mật. Hãy xem thông tin dị ứng trước khi đặt.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        item { Text("Bản xem thử · Cửa hàng, tồn kho và khoảng cách là dữ liệu mẫu.", Modifier.padding(horizontal = 24.dp), fontSize = 10.sp, lineHeight = 15.sp, color = colors.onSurfaceVariant) }
    }
}

@Composable
private fun DiscoveryCard(box: DemoBox, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), shape = RoundedCornerShape(20.dp),
        color = c.surface, border = BorderStroke(1.dp, c.outlineVariant.copy(alpha = .7f))) {
        Column {
            CatalogPhoto(box, Modifier.fillMaxWidth().height(if (LocalDensity.current.fontScale > 1.2f) 130.dp else 174.dp), showCount = true)
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StoreMonogram(box)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(box.store, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.25).sp)
                        Text(box.category.label, fontSize = 11.sp, lineHeight = 17.sp, color = c.onSurfaceVariant)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Giá trị tối thiểu", fontSize = 10.sp, lineHeight = 15.sp, color = c.onSurfaceVariant)
                        Text(money(box.minimumValue), fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
                    }
                    Text(money(box.price), fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-.7).sp, fontWeight = FontWeight.Bold, color = c.primary)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CatalogGlyph(CatalogIcon.CLOCK, Modifier.size(14.dp), c.onSurfaceVariant)
                    Text("${box.pickup}", fontSize = 11.sp, color = c.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(distance(box.distanceMeters), fontSize = 11.sp, color = c.onSurfaceVariant)
                }
                HorizontalDivider(color = c.outlineVariant.copy(alpha = .6f))
            }
        }
    }
}

@Composable
internal fun CatalogPhoto(box: DemoBox, modifier: Modifier = Modifier, showCount: Boolean = false) {
    Box(modifier) {
        Image(painterResource(box.photo()), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .4f)), startY = 100f)))
        if (showCount) Surface(Modifier.align(Alignment.TopStart).padding(12.dp), color = Color.White, shape = RoundedCornerShape(50)) {
            Text("Còn ${box.remaining} hộp", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), fontSize = 10.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        }
        Text("Ảnh minh họa", Modifier.align(Alignment.BottomEnd).padding(12.dp), color = Color.White, fontSize = 9.sp, lineHeight = 13.sp)
    }
}

@Composable
internal fun StoreMonogram(box: DemoBox) {
    val c = MaterialTheme.colorScheme
    Surface(shape = CircleShape, color = c.primaryContainer) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { Text(box.initials, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.primary) }
    }
}

@Composable
private fun StoreResult(box: DemoBox, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(box.photo()), null, Modifier.size(68.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(box.store, style = MaterialTheme.typography.titleSmall)
                Text("${box.district} · ${distance(box.distanceMeters)}", style = MaterialTheme.typography.bodySmall)
                Text("Xem hộp đang có", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
internal fun CatalogEmpty(title: String, body: String, action: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) { CatalogGlyph(CatalogIcon.BAG, tint = MaterialTheme.colorScheme.primary) }
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onClick) { Text(action) }
    }
}
