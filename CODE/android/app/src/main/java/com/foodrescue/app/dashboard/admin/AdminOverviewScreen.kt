package com.foodrescue.app.dashboard.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodrescue.app.core.designsystem.component.*
import com.foodrescue.app.feature.store.domain.*
import com.foodrescue.app.feature.store.presentation.*
import com.foodrescue.app.feature.store.presentation.component.*

@Composable
internal fun AdminOverviewScreen(state: StoreApplicationsState, onQueue: (ApplicationFilter) -> Unit,
    onOpen: (String) -> Unit, onRefresh: () -> Unit, modifier: Modifier = Modifier,
    complaintSummary: @Composable () -> Unit = {}) {
    val pending = state.applications.filter { it.store.status == StoreReviewStatus.PENDING }
    val activities = state.applications.flatMap { application -> application.store.reviewHistory.map { application to it } }
        .sortedByDescending { it.second.atMillis }.take(4)
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Tổng quan", style = MaterialTheme.typography.headlineLarge)
                    Text("Hồ sơ & đối tác", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onRefresh, enabled = !state.busy) { Text("Làm mới") }
            }
        }
        if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (state.error != null) item { StoreErrorMessage(state.error) }
        item { complaintSummary() }
        if (!state.loading) {
            item {
                Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF073F38), MaterialTheme.colorScheme.primary)),
                    RoundedCornerShape(26.dp)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("HÀNG CHỜ XÉT DUYỆT", color = Color(0xFFCCE2D9), style = MaterialTheme.typography.labelMedium, letterSpacing = 1.5.sp)
                        Spacer(Modifier.weight(1f)); FoodRescueGlyph(FoodRescueIcon.STORE, tint = Color(0xFFB8DAD0))
                    }
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("${pending.size}".padStart(2, '0'), color = Color.White, fontSize = 60.sp, lineHeight = 64.sp, fontWeight = FontWeight.SemiBold)
                        Text("hồ sơ\nchờ quyết định", color = Color.White, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    Text(if (pending.isEmpty()) "Hàng chờ đã được xử lý." else "Hồ sơ sớm nhất · ${reviewDate(pending.first().store.submittedAtMillis)}",
                        style = MaterialTheme.typography.bodySmall, color = Color(0xFFD8EAE2))
                    Button({ onQueue(ApplicationFilter.PENDING) }, Modifier.fillMaxWidth().heightIn(min = 50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE7F0C3), contentColor = Color(0xFF163D31)),
                        shape = RoundedCornerShape(14.dp)) {
                        Text(if (pending.isEmpty()) "Xem hồ sơ cửa hàng" else "Xét duyệt hồ sơ", Modifier.weight(1f))
                        FoodRescueGlyph(FoodRescueIcon.ARROW)
                    }
                }
            }
            item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryTile("Đã duyệt", state.count(StoreReviewStatus.APPROVED), FoodRescueIcon.CHECK,
                    { onQueue(ApplicationFilter.APPROVED) }, Modifier.weight(1f))
                SummaryTile("Cần bổ sung", state.count(StoreReviewStatus.CHANGES_REQUIRED), FoodRescueIcon.EDIT,
                    { onQueue(ApplicationFilter.CHANGES) }, Modifier.weight(1f))
            } }
            if (pending.isNotEmpty()) {
                item { StoreSectionTitle("Ưu tiên xử lý") }
                items(pending.take(3), key = { it.accountKey }) { item -> StoreApplicationCard(item, { onOpen(item.accountKey) }, enabled = !state.busy) }
            }
            item {
                FoodRescueCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        StoreSectionTitle("Hoạt động gần đây")
                        FoodRescueGlyph(FoodRescueIcon.CLOCK, tint = MaterialTheme.colorScheme.outline)
                    }
                    if (activities.isEmpty()) Text("Các lần gửi hồ sơ và quyết định xét duyệt sẽ được ghi nhận tại đây.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    activities.forEachIndexed { index, (application, event) ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        TextButton({ onOpen(application.accountKey) }, enabled = !state.busy, contentPadding = PaddingValues(0.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(event.action.label, style = MaterialTheme.typography.labelLarge)
                                Text(application.store.profile.name, color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyMedium)
                                Text(reviewDate(event.atMillis), color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            FoodRescueGlyph(FoodRescueIcon.ARROW)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryTile(title: String, count: Int, icon: FoodRescueIcon, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick, modifier, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FoodRescueGlyph(icon, tint = MaterialTheme.colorScheme.primary)
            Text(count.toString(), style = MaterialTheme.typography.headlineMedium)
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
