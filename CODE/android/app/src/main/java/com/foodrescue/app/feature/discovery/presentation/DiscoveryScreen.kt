package com.foodrescue.app.feature.discovery.presentation

import com.foodrescue.app.core.designsystem.component.FoodRescueGlyph
import com.foodrescue.app.core.designsystem.component.FoodRescueIcon
import com.foodrescue.app.core.designsystem.component.FoodRescueEmptyState
import com.foodrescue.app.feature.surprisebox.presentation.component.SurpriseBoxCard
import com.foodrescue.app.feature.surprisebox.presentation.component.categoryPhoto
import com.foodrescue.app.core.util.money
import com.foodrescue.app.core.util.distance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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

@Composable
internal fun CatalogDiscoveryScreen(s: DiscoveryListUiState, searchMode: Boolean, onSearch: (String) -> Unit,
    onOpenSearch: () -> Unit, onFilters: () -> Unit, onArea: () -> Unit,
    onMap: () -> Unit, onBox: (Int) -> Unit, onReset: () -> Unit,
    onKind: (Boolean) -> Unit, onStore: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val largeText = LocalDensity.current.fontScale > 1.2f
    val storeResults = s.storeResults
    LazyColumn(contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(Modifier.padding(horizontal = 24.dp).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Surface(onClick = onArea, color = Color.Transparent, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FoodRescueGlyph(FoodRescueIcon.PIN, Modifier.size(20.dp), colors.primary)
                            Column(Modifier.weight(1f)) {
                                Text("KHU VỰC NHẬN HỘP", fontSize = 9.sp, lineHeight = 14.sp, letterSpacing = 1.sp, color = colors.onSurfaceVariant, fontWeight = FontWeight.Medium)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(s.district, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    FoodRescueGlyph(FoodRescueIcon.CHEVRON, Modifier.size(17.dp))
                                }
                            }
                        }
                    }
                    Surface(onClick = onMap, color = colors.surface, shape = CircleShape, border = BorderStroke(1.dp, colors.outlineVariant)) {
                        Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) { FoodRescueGlyph(FoodRescueIcon.MAP, tint = colors.primary, description = "Xem bản đồ") }
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
                            leadingIcon = { FoodRescueGlyph(FoodRescueIcon.SEARCH, Modifier.size(20.dp)) },
                            trailingIcon = if (s.query.isNotEmpty()) ({ IconButton(onClick = { onSearch("") }) { FoodRescueGlyph(FoodRescueIcon.CLOSE, Modifier.size(18.dp), description = "Xóa từ khóa") } }) else null,
                            shape = RoundedCornerShape(14.dp), textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = colors.surface, focusedContainerColor = colors.surface,
                                unfocusedBorderColor = colors.outlineVariant))
                    } else {
                        Surface(onClick = onOpenSearch, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                            color = colors.surface, border = BorderStroke(1.dp, colors.outlineVariant)) {
                            Row(Modifier.heightIn(min = 54.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FoodRescueGlyph(FoodRescueIcon.SEARCH, Modifier.size(20.dp), colors.onSurfaceVariant)
                                Text(s.query.ifBlank { "Hộp, cửa hàng…" }, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Surface(onClick = onFilters, color = if (s.filterCount > 0) colors.primaryContainer else colors.surface,
                        shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, colors.outlineVariant)) {
                        Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
                            FoodRescueGlyph(FoodRescueIcon.FILTER, tint = colors.primary, description = "Bộ lọc")
                            if (s.filterCount > 0) Badge(Modifier.align(Alignment.TopEnd).padding(4.dp), containerColor = colors.primary) { Text(s.filterCount.toString()) }
                        }
                    }
                }
            }
        }
        if (searchMode) item {
            Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !storeResults, onClick = { onKind(false) }, label = { Text("Hộp bất ngờ") })
                FilterChip(selected = storeResults, onClick = { onKind(true) }, label = { Text("Cửa hàng") })
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (searchMode) "Kết quả tìm kiếm" else "Hộp quanh bạn", Modifier.weight(1f).semantics { heading() },
                    fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-.3).sp, fontWeight = FontWeight.SemiBold)
                Text("${s.total} ${if (searchMode && storeResults) "nơi" else "hộp"}", fontSize = 12.sp, color = colors.onSurfaceVariant)
            }
        }
        if (s.filterCount > 0) item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(s.filterSummary, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                TextButton(onClick = onReset) { Text("Đặt lại") }
            }
        }
        if (searchMode && storeResults) items(s.shops, key = { it.id }) { shop -> StoreResult(shop) { onStore(shop.id) } }
        else items(s.boxes, key = { it.id }) { box -> SurpriseBoxCard(box, Modifier.padding(horizontal = 24.dp)) { onBox(box.id) } }
        if (s.total == 0L) item {
            FoodRescueEmptyState(if (storeResults) "Chưa tìm thấy cửa hàng phù hợp" else "Chưa tìm thấy hộp phù hợp",
                "Thử từ khóa khác, đổi khu vực hoặc điều chỉnh bộ lọc.", "Xóa tìm kiếm và bộ lọc", onReset)
        }
        item {
            Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                FoodRescueGlyph(FoodRescueIcon.BAG, Modifier.size(18.dp), colors.primary)
                Text("Món cụ thể trong hộp được giữ bí mật. Hãy xem thông tin dị ứng trước khi đặt.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }

    }
}

@Composable
private fun StoreResult(box: DiscoveryShop, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(categoryPhoto(box.category)), null, Modifier.size(68.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(box.name, style = MaterialTheme.typography.titleSmall)
                Text(box.area + if (box.distanceMeters >= 0) " · ${distance(box.distanceMeters)}" else "", style = MaterialTheme.typography.bodySmall)
                Text("${box.count} lựa chọn · Từ ${money(box.fromPrice)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
