package com.foodrescue.app.feature.menu.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.feature.menu.domain.*
import com.foodrescue.app.feature.menu.presentation.component.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MenuCatalogScreen(storeName: String, state: MenuUiState, onBack: () -> Unit, onAdd: () -> Unit,
    onItem: (String) -> Unit, onDocuments: () -> Unit, onSearch: (String) -> Unit, onCategory: (MenuCategory?) -> Unit,
    onStatus: (MenuStatus?) -> Unit, onSort: (MenuSort) -> Unit, onReset: () -> Unit, modifier: Modifier = Modifier) {
    val visible = state.visibleItems
    Scaffold(modifier, containerColor = MaterialTheme.colorScheme.background, topBar = {
        TopAppBar(title = { Text("Menu nội bộ", style = MaterialTheme.typography.titleMedium) }, navigationIcon = {
            IconButton(onBack) { FoodRescueGlyph(FoodRescueIcon.BACK, description = "Quay lại") }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
    }, bottomBar = {
        Surface(modifier = Modifier.fillMaxWidth(), shadowElevation = 6.dp) {
            FoodRescueButton("Thêm món", onAdd, Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 24.dp, vertical = 12.dp), enabled = !state.busy)
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = MenuForest) {
                    Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(MenuForest, Color(0xFF235B44)))).padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(storeName, color = MenuMint, style = MaterialTheme.typography.labelLarge)
                        HorizontalDivider(color = Color.White.copy(alpha = .18f))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            MenuCount(state.menu.items.count { it.status == MenuStatus.ACTIVE }, "Đang kinh doanh", Modifier.weight(1f))
                            MenuCount(state.menu.items.count { it.status == MenuStatus.INACTIVE }, "Ngừng kinh doanh", Modifier.weight(1f))
                        }
                    }
                }
            }
            item {
                OutlinedCard(onClick = onDocuments, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        FoodRescueGlyph(FoodRescueIcon.RECEIPT, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Bảng giá & tài liệu", style = MaterialTheme.typography.titleSmall)
                            Text("${state.menu.documents.size} tài liệu tham chiếu", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FoodRescueGlyph(FoodRescueIcon.CHEVRON, modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (state.error != null) item { MenuError(state.error) }
            if (state.menu.items.isNotEmpty()) {
            item {
                FoodRescueTextField("Tìm món", state.query, onSearch, placeholder = "Tên món hoặc mô tả", trailingIcon = {
                    if (state.query.isNotEmpty()) IconButton({ onSearch("") }) { FoodRescueGlyph(FoodRescueIcon.CLOSE, description = "Xóa tìm kiếm") }
                    else FoodRescueGlyph(FoodRescueIcon.SEARCH)
                })
            }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(state.status == null, { onStatus(null) }, label = { Text("Tất cả") })
                    MenuStatus.entries.forEach { value -> FilterChip(state.status == value, { onStatus(value) }, label = { Text(value.label) }) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MenuFilter(state.category, listOf(null) + MenuCategory.entries, { it?.label ?: "Nhóm món" }, onCategory, Modifier.weight(1f))
                    MenuFilter(state.sort, MenuSort.entries, { it.label }, onSort, Modifier.weight(1f))
                }
            }
            item { Text("${visible.size} món", style = MaterialTheme.typography.titleMedium) }
            }
            if (visible.isEmpty()) item {
                FoodRescueCard {
                    FoodRescueGlyph(FoodRescueIcon.RECEIPT, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(if (state.menu.items.isEmpty()) "Bắt đầu với món đầu tiên" else "Không tìm thấy món", style = MaterialTheme.typography.titleMedium)
                    Text(if (state.menu.items.isEmpty()) "Lưu món ăn, giá niêm yết và ảnh để quản lý thực đơn của cửa hàng."
                        else "Thử tên món khác hoặc thay đổi bộ lọc.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.menu.items.isNotEmpty()) TextButton(onReset) { Text("Xóa bộ lọc") }
                }
            }
            items(visible, key = { it.id }) { item -> MenuItemCard(item, { onItem(item.id) }) }
        }
    }
}

@Composable
private fun <T> MenuFilter(value: T, options: List<T>, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton({ expanded = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp), contentPadding = PaddingValues(10.dp), shape = RoundedCornerShape(12.dp)) {
            Text(label(value), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            FoodRescueGlyph(FoodRescueIcon.CHEVRON, Modifier.size(16.dp))
        }
        DropdownMenu(expanded, { expanded = false }, Modifier.heightIn(max = 360.dp)) {
            options.forEach { option -> DropdownMenuItem(text = { Text(label(option)) }, onClick = { expanded = false; onSelect(option) },
                trailingIcon = { if (value == option) FoodRescueGlyph(FoodRescueIcon.CHECK, Modifier.size(18.dp)) }) }
        }
    }
}

@Composable
private fun MenuCount(value: Int, label: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(value.toString().padStart(2, '0'), color = Color.White, style = MaterialTheme.typography.headlineLarge)
        Text(label, color = Color(0xFFD3E4D9), style = MaterialTheme.typography.bodySmall)
    }
}
