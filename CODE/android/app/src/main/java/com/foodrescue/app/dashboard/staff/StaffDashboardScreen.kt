package com.foodrescue.app.dashboard.staff

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.foodrescue.app.core.util.money
import com.foodrescue.app.feature.staff.presentation.*
import com.foodrescue.app.feature.staff.domain.EmployeePermission
import com.foodrescue.app.feature.surprisebox.domain.*
import com.foodrescue.app.feature.surprisebox.presentation.component.boxDateTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Forest = Color(0xFF133E32)
private val Mint = Color(0xFFD8EDB4)

@Composable
internal fun StaffDashboardScreen(state: StaffPackingUiState, onBegin: (String) -> Unit, onResume: () -> Unit,
    onOpenBox: (String) -> Unit, onHandover: () -> Unit, modifier: Modifier = Modifier) {
    val batches = state.workspace.batches.filter { it.control != BatchControl.DRAFT && it.pickupEnd > state.now }
    val needed = batches.sumOf { (it.sold - it.handedOver - it.eligible).coerceAtLeast(0) }
    val ready = batches.sumOf { it.eligible }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Ca làm việc", style = MaterialTheme.typography.headlineLarge)
                Text(SimpleDateFormat("EEEE, dd/MM", Locale.forLanguageTag("vi-VN")).format(Date(state.now)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(14.dp)) {
                FoodRescueGlyph(FoodRescueIcon.BAG, Modifier.padding(14.dp), tint = Forest)
            }
        }
        Surface(shape = RoundedCornerShape(26.dp), color = Forest) {
            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Forest, Color(0xFF245C47)))).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("ƯU TIÊN TRONG CA", color = Mint, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(needed.toString().padStart(2, '0'), color = Color.White, fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold)
                    Text("hộp cần\nchuẩn bị", color = Color(0xFFDCE9DF), style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 5.dp))
                }
                Text("Cho các đơn đã thanh toán", color = Color(0xFFDCE9DF), style = MaterialTheme.typography.bodySmall)
                HorizontalDivider(color = Color.White.copy(alpha = .18f))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f)) { Text("$ready hộp", color = Color.White, style = MaterialTheme.typography.titleSmall); Text("Còn hạn, chưa giao", color = Color(0xFFDCE9DF), style = MaterialTheme.typography.bodySmall) }
                    Column(Modifier.weight(1f)) { Text("${state.batchesToPrepare.size} đợt", color = Color.White, style = MaterialTheme.typography.titleSmall); Text("Có thể chuẩn bị", color = Color(0xFFDCE9DF), style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        if (EmployeePermission.VIEW_ORDERS in state.workspace.access?.permissions.orEmpty()) FoodRescueCard {
            Text("Bàn giao cho khách", style = MaterialTheme.typography.titleLarge)
            Text("Đối chiếu mã nhận hàng và chọn hộp cho các đơn đã thanh toán.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FoodRescueButton("Mở danh sách bàn giao", onHandover, Modifier.fillMaxWidth())
        }
        if (state.draft.id.isNotEmpty()) Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFFF3ECD7)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Đang chuẩn bị", style = MaterialTheme.typography.labelMedium, color = Color(0xFF735825))
                Text(state.batch?.template?.name ?: "Hộp chưa hoàn tất", style = MaterialTheme.typography.titleMedium)
                Text("Bước ${state.step + 1}/3 · Nội dung đã được giữ lại", style = MaterialTheme.typography.bodySmall)
                TextButton(onResume) { Text("Tiếp tục đóng gói") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Đợt cần chuẩn bị", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text("${state.batchesToPrepare.size} đợt", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        if (state.batchesToPrepare.isEmpty() && state.canPrepare) FoodRescueCard {
            FoodRescueGlyph(FoodRescueIcon.CHECK, tint = MaterialTheme.colorScheme.primary)
            Text("Chưa có đợt cần đóng gói", style = MaterialTheme.typography.titleMedium)
            Text("Các đợt do chủ cửa hàng mở sẽ xuất hiện tại đây khi còn số lượng cần chuẩn bị.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.batchesToPrepare.forEach { batch ->
            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(shape = RoundedCornerShape(8.dp), color = if (state.now >= batch.pickupStart) Color(0xFFF3ECD7) else Color(0xFFE6EEE3)) {
                            Text(if (state.now >= batch.pickupStart) "ĐANG TRONG GIỜ NHẬN" else "CHUẨN BỊ TRƯỚC GIỜ NHẬN", Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall, color = Forest)
                        }
                    }
                    Text(batch.template.name, style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FoodRescueGlyph(FoodRescueIcon.CLOCK, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Nhận ${boxDateTime(batch.pickupStart)}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Giá trị mỗi hộp từ ${money(batch.template.minimumValue)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row { Text("Tiến độ chuẩn bị", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); Text("${batch.prepared}/${batch.packingTarget} hộp", style = MaterialTheme.typography.labelMedium) }
                    LinearProgressIndicator(progress = { (batch.prepared.toFloat() / batch.packingTarget.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(5.dp))
                    if (batch.rejected + batch.expired > 0) Text("${batch.rejected} hộp không đạt · ${batch.expired} hộp hết hạn", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    FoodRescueButton(if (state.draft.id.isEmpty()) "Đóng gói hộp" else "Tiếp tục bản nháp", { onBegin(batch.id) }, Modifier.fillMaxWidth(), enabled = state.canPrepare)
                }
            }
        }
        val recent = state.workspace.boxes.sortedByDescending { it.packedAt }.take(3)
        if (recent.isNotEmpty()) {
            Text("Vừa hoàn tất", style = MaterialTheme.typography.titleLarge)
            recent.forEach { box -> PackedBoxRow(box, state.now, { onOpenBox(box.id) }) }
        }
    }
}
