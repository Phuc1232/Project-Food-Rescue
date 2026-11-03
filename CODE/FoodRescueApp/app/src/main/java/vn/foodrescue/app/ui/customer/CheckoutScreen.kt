package vn.foodrescue.app.ui.customer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CheckoutScreen(box: DemoBox, vm: CustomerViewModel, onBack: () -> Unit, onOrder: (String) -> Unit) {
    var count by rememberSaveable(box.id) { mutableIntStateOf(1) }
    var plan by rememberSaveable(box.id) { mutableStateOf(PaymentPlan.FULL) }
    var method by rememberSaveable(box.id) { mutableStateOf(PaymentMethod.VNPAY_QR) }
    var accepted by rememberSaveable(box.id) { mutableStateOf(false) }
    var methodOpen by rememberSaveable { mutableStateOf(false) }
    var policyOpen by rememberSaveable { mutableStateOf(false) }
    val pickupStart = rememberSaveable(box.id) { previewPickup(box, System.currentTimeMillis()).first }
    val pickup = pickupStart to pickupStart + 2 * 60 * 60_000L
    val available = vm.available(box, pickup.first)
    val policy = remember { OrderPolicy() }
    val total = box.price.toLong() * count
    val deposit = policy.deposit(total)
    val payNow = if (plan == PaymentPlan.FULL) total else deposit
    CustomerPage("Xác nhận đặt hộp", onBack, footer = {
        PaymentFooter(payNow, "Tiếp tục", accepted && count <= available, total - payNow) {
            vm.place(box, count, plan, method, pickup)?.let(onOrder)
        }
    }) {
        PaymentPreviewLabel()
        CheckoutSummary(box, count, available, pickup.first, pickup.second, { count-- }, { count++ })
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PaymentSectionTitle("Chọn khoản thanh toán")
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PaymentPlanOption("Trả toàn bộ", total, "Đến nhận hộp, không cần trả thêm.", plan == PaymentPlan.FULL) { plan = PaymentPlan.FULL }
                PaymentPlanOption("Đặt cọc ${policy.depositPercent}%", deposit, "Còn ${money(total - deposit)} trả tại cửa hàng.", plan == PaymentPlan.DEPOSIT) { plan = PaymentPlan.DEPOSIT }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PaymentSectionTitle("Phương thức thanh toán")
            SelectedPaymentMethod(method) { methodOpen = true }
        }
        CustomerCard {
            PaymentSectionTitle("Chi tiết số tiền")
            AmountRow("${money(box.price)} × $count hộp", total)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            AmountRow(if (plan == PaymentPlan.DEPOSIT) "Đặt cọc hôm nay" else "Trả toàn bộ hôm nay", payNow, true)
            AmountRow("Trả khi nhận hộp", total - payNow)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                CatalogGlyph(CatalogIcon.CLOCK, Modifier.size(18.dp), MaterialTheme.colorScheme.primary)
                Text("Hộp được giữ ${policy.holdMinutes} phút từ bước tiếp theo.", fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { policyOpen = true }, contentPadding = PaddingValues(0.dp)) {
                Text("Xem chính sách và thông tin dị ứng", fontSize = 12.sp)
            }
            Row(Modifier.fillMaxWidth().toggleable(value = accepted, role = Role.Checkbox, onValueChange = { accepted = it }).padding(vertical = 8.dp),
                verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Checkbox(accepted, onCheckedChange = null, modifier = Modifier.padding(top = 2.dp).size(24.dp))
                Text("Tôi đồng ý với giờ nhận, thông tin dị ứng và chính sách của đơn.", Modifier.weight(1f), fontSize = 12.sp, lineHeight = 19.sp)
            }
        }
    }
    if (methodOpen) PaymentMethodSheet(method, { method = it; methodOpen = false }, { methodOpen = false })
    if (policyOpen) ModalBottomSheet(onDismissRequest = { policyOpen = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Trước khi đặt hộp", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            PaymentSectionTitle("Nhận tại cửa hàng")
            Text("Đến nhận trong khung giờ đã chọn. Món cụ thể được giữ bí mật đến khi nhận hộp.", style = MaterialTheme.typography.bodyMedium)
            PaymentSectionTitle("Thông tin dị ứng")
            Text(box.allergens, style = MaterialTheme.typography.bodyMedium)
            PaymentSectionTitle("Hủy và hoàn tiền")
            Text("Hủy hợp lệ trước giờ nhận: hoàn toàn bộ khoản đã trả qua ứng dụng. Sau giờ nhận, yêu cầu cần được xem xét. Kết quả hoàn tiền được theo dõi riêng trong chi tiết đơn.", style = MaterialTheme.typography.bodyMedium)
            PaymentSectionTitle("Không đến nhận hộp")
            Text("Nếu được xác minh không đến nhận, khoản giữ lại là ${money(deposit)} dù bạn trả toàn bộ hay đặt cọc. Phần vượt khoản này được hoàn và bạn không phải trả thêm. Bạn có thể phản ánh trong 48 giờ từ khi nhận thông báo.", style = MaterialTheme.typography.bodyMedium)
            Text("Cọc ${policy.depositPercent}% và giữ hộp ${policy.holdMinutes} phút là cấu hình của bản thử, chưa phải chính sách phát hành.",
                fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PrimaryAction("Đã hiểu") { policyOpen = false }
        }
    }
}
