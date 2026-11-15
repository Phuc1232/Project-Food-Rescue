package com.foodrescue.app.feature.finance.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.core.util.money
import com.foodrescue.app.feature.finance.domain.*
import com.foodrescue.app.feature.store.presentation.component.StoreSectionTitle

@Composable
internal fun RevenuePeriodPicker(state: RevenueUiState, onPeriod: (RevenuePeriod) -> Unit,
    onMove: (Int) -> Unit, onCurrent: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RevenuePeriod.entries.forEach { period -> FilterChip(state.period == period, { onPeriod(period) },
                label = { Text(period.label) }, modifier = Modifier.weight(1f)) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ onMove(-1) }, enabled = state.offset > -1200) { FoodRescueGlyph(FoodRescueIcon.BACK, description = "Kỳ trước") }
            Text(if (state.period == RevenuePeriod.DAY) revenueDate(state.window.start) else revenueRange(state.window),
                Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
            IconButton({ onMove(1) }, enabled = state.offset < 0) { FoodRescueGlyph(FoodRescueIcon.CHEVRON, description = "Kỳ sau") }
        }
        if (state.offset != 0) TextButton(onCurrent) { Text("Về kỳ hiện tại") }
    }
}

@Composable
internal fun RevenueChips(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label -> FilterChip(selected == index, { onSelect(index) }, label = { Text(label) }) }
    }
}

@Composable
internal fun RevenueBadge(text: String, modifier: Modifier = Modifier, negative: Boolean = false) {
    Surface(modifier, shape = RoundedCornerShape(8.dp), color = if (negative) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium,
            color = if (negative) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
internal fun RevenueMoneyLine(label: String, amount: Long, modifier: Modifier = Modifier, emphasized: Boolean = false) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(money(amount), style = if (emphasized) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
internal fun RevenueEntryCard(entry: RevenueEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick, modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.order.reference, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                FoodRescueGlyph(FoodRescueIcon.CHEVRON, tint = MaterialTheme.colorScheme.outline)
            }
            Text(entry.order.boxSnapshot?.name ?: "Surprise Box", style = MaterialTheme.typography.bodyMedium)
            Text("${entry.order.quantity} hộp · ${revenueDate(entry.paidAt, "HH:mm · dd/MM/yyyy")}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            RevenueBadge(entry.status.label)
            RevenueMoneyLine(if (entry.status == RevenueEntryStatus.ELIGIBLE) "Cửa hàng được hưởng" else "Khách đã thanh toán",
                if (entry.status == RevenueEntryStatus.ELIGIBLE) entry.net else entry.gross)
        }
    }
}

@Composable
internal fun RevenueEarningsChart(snapshot: RevenueSnapshot, window: RevenueWindow, modifier: Modifier = Modifier) {
    val days = generateSequence(window.start) { it + 86_400_000L }.takeWhile { it < window.endExclusive }.toList()
    val buckets = days.map { start -> revenueSum(snapshot.entries.filter { it.earnedAt?.let { date -> date in start until start + 86_400_000L } == true }.map { it.net }) }
    val largest = buckets.maxOrNull() ?: 0
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.primaryContainer
    FoodRescueCard(modifier) {
        StoreSectionTitle("Khoản được hưởng theo ngày")
        Text(if (largest == 0L) "Chưa có đơn đủ điều kiện trong kỳ này." else "Ngày cao nhất · ${money(largest)}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(Modifier.fillMaxWidth().height(110.dp).semantics {
            contentDescription = days.zip(buckets).joinToString("; ") { (day, amount) -> "${revenueDate(day, "dd/MM")}: ${money(amount)}" }
        }) {
            val cell = size.width / days.size.coerceAtLeast(1)
            val width = (cell * .58f).coerceAtMost(32.dp.toPx())
            buckets.forEachIndexed { index, amount ->
                val left = cell * index + (cell - width) / 2
                drawRoundRect(track, Offset(left, 0f), Size(width, size.height), CornerRadius(5.dp.toPx()))
                if (largest > 0 && amount > 0) {
                    val height = (amount.toDouble() / largest * size.height).toFloat().coerceAtLeast(2.dp.toPx())
                    drawRoundRect(color, Offset(left, size.height - height), Size(width, height), CornerRadius(5.dp.toPx()))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(revenueDate(window.start, "dd/MM"), style = MaterialTheme.typography.labelSmall)
            if (days.size > 1) Text(revenueDate(window.endExclusive - 1, "dd/MM"), style = MaterialTheme.typography.labelSmall)
        }
    }
}
