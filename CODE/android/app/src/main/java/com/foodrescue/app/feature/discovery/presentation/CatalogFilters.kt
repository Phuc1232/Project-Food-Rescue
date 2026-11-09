package com.foodrescue.app.feature.discovery.presentation

import com.foodrescue.app.feature.discovery.domain.*
import com.foodrescue.app.feature.surprisebox.domain.BoxCategory
import com.foodrescue.app.core.util.money

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val FilterSaver = listSaver<DiscoveryFilters, Int>(
    save = { listOf(it.category?.ordinal ?: -1, it.price?.min ?: -1, it.price?.max ?: -1, it.pickup.ordinal, it.sort.ordinal) },
    restore = { DiscoveryFilters(BoxCategory.entries.getOrNull(it[0]),
        if (it[1] < 0) null else PriceRange(it[1], it[2]), PickupBand.entries[it[3]], CatalogSort.entries[it[4]]) },
)

@Composable
internal fun FilterSheet(state: DiscoveryUiState, onApply: (DiscoveryFilters) -> Unit) {
    var draft by rememberSaveable(state.filters, stateSaver = FilterSaver) { mutableStateOf(state.filters) }
    val priceCeiling = remember(state.source, state.filters.price) { maxOf(catalogPriceCeiling(state.source), state.filters.price?.max ?: 0) }
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
