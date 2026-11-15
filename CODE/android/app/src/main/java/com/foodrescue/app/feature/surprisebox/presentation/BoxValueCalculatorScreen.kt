package com.foodrescue.app.feature.surprisebox.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.core.util.money
import com.foodrescue.app.feature.menu.domain.MenuItem
import com.foodrescue.app.feature.menu.domain.MenuStatus
import com.foodrescue.app.feature.menu.domain.MenuUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BoxValueCalculatorScreen(
    state: BoxValueCalculatorState, onBack: () -> Unit, onSearch: (String) -> Unit,
    onQuantity: (String, String?) -> Unit, onRetry: () -> Unit, onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val estimate = state.estimate
    Scaffold(modifier = modifier, containerColor = colors.background, topBar = {
        TopAppBar(title = { Text("Tính giá trị hộp", style = MaterialTheme.typography.titleMedium) }, navigationIcon = {
            IconButton(onClick = onBack) { FoodRescueGlyph(FoodRescueIcon.BACK, description = "Quay lại") }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background))
    }, bottomBar = {
        Surface(shadowElevation = 6.dp) {
            Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tổng: ${money(estimate.total)}", style = MaterialTheme.typography.titleMedium)
                FoodRescueButton(if (state.loading) "Đang tải menu…" else "Áp dụng giá trị", onApply,
                    Modifier.fillMaxWidth(), enabled = state.canApply)
            }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Surface(color = colors.primary, contentColor = colors.onPrimary, shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("GIÁ TRỊ THAM CHIẾU", style = MaterialTheme.typography.labelMedium)
                        Text(money(estimate.total), style = MaterialTheme.typography.headlineLarge)
                        Text("${estimate.lineValues.size} món đã tính", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (state.notice != null) item {
                Text(state.notice, color = colors.primary, style = MaterialTheme.typography.bodyMedium)
            }
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (state.error != null) item {
                FoodRescueCard {
                    Text(state.error, color = colors.error)
                    OutlinedButton(onClick = onRetry) { Text("Thử lại") }
                }
            }
            if (!state.loading && state.error == null) {
                if (state.menu.items.none { it.status == MenuStatus.ACTIVE } && state.quantities.isEmpty()) {
                    item { FoodRescueCard {
                        Text("Chưa có món đang kinh doanh", style = MaterialTheme.typography.titleMedium)
                        Text("Thêm món và giá vào Menu nội bộ để tính giá trị hộp.", color = colors.onSurfaceVariant)
                    } }
                } else {
                    item { FoodRescueTextField("Tìm món trong menu", state.query, onSearch, placeholder = "Tên món") }
                    if (estimate.invalidIds.isNotEmpty()) item {
                        Text("Kiểm tra số lượng hoặc bỏ chọn món không còn kinh doanh.", color = colors.error)
                    }
                    if (estimate.total > 999_999_999) item {
                        Text("Giá trị vượt 999.999.999 đ. Giảm số lượng để áp dụng.", color = colors.error)
                    }
                    if (state.visibleItems.isEmpty()) item { Text("Không tìm thấy món phù hợp.", color = colors.onSurfaceVariant) }
                    items(state.visibleItems, key = { it.id }) { item ->
                        ValueMenuRow(item, state.quantities[item.id], item.id in state.quantities,
                            estimate.lineValues[item.id], item.id in estimate.invalidIds, onQuantity)
                    }
                }
            }
        }
    }
}

@Composable
private fun ValueMenuRow(item: MenuItem, quantity: String?, selected: Boolean, lineValue: Long?, invalid: Boolean,
    onQuantity: (String, String?) -> Unit, modifier: Modifier = Modifier) {
    val active = item.status == MenuStatus.ACTIVE
    val unit = when (item.unit) {
        MenuUnit.PORTION -> "phần"; MenuUnit.PIECE -> "cái"; MenuUnit.CUP -> "ly"; MenuUnit.BOTTLE -> "chai"
        MenuUnit.BOX -> "hộp"; MenuUnit.PACK -> "gói"; MenuUnit.KILOGRAM -> "kg"
    }
    FoodRescueCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium)
                Text("${money(item.price)} / $unit", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!active) Text("Ngừng kinh doanh", color = MaterialTheme.colorScheme.error)
            }
            Checkbox(selected, { onQuantity(item.id, if (it) "1" else null) }, enabled = active || selected,
                modifier = Modifier.semantics { contentDescription = "Chọn ${item.name}" })
        }
        if (selected && active) {
            FoodRescueTextField("Số lượng · ${item.name}", quantity.orEmpty(), { onQuantity(item.id, it) },
                error = if (invalid) if (item.unit == MenuUnit.KILOGRAM) "Nhập tối đa 9.999 kg, đến 3 chữ số thập phân."
                    else "Nhập số nguyên từ 0 đến 9.999." else null,
                keyboardOptions = KeyboardOptions(keyboardType = if (item.unit == MenuUnit.KILOGRAM) KeyboardType.Decimal else KeyboardType.Number))
            Text("${quantity.orEmpty()} $unit × ${money(item.price)} = ${lineValue?.let { money(it) } ?: "—"}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}
