package vn.foodrescue.app.ui.customer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun CheckoutScreen(box: DemoBox, vm: CustomerViewModel, onBack: () -> Unit, onOrder: (String) -> Unit) {
    var count by rememberSaveable(box.id) { mutableIntStateOf(1) }
    var plan by rememberSaveable { mutableStateOf(PaymentPlan.FULL) }
    var method by rememberSaveable { mutableStateOf(PaymentMethod.CARD) }
    var accepted by rememberSaveable { mutableStateOf(false) }
    var policyOpen by rememberSaveable { mutableStateOf(false) }
    val pickupStart = rememberSaveable(box.id) { previewPickup(box, System.currentTimeMillis()).first }
    val pickup = pickupStart to pickupStart + 2 * 60 * 60_000L
    val available = vm.available(box, pickup.first)
    val policy = remember { OrderPolicy() }
    val total = box.price.toLong() * count
    val payNow = if (plan == PaymentPlan.FULL) total else policy.deposit(total)
    CustomerPage("Xác nhận đặt hộp", onBack, footer = {
        AmountRow("Trả qua ứng dụng", payNow, true)
        Spacer(Modifier.height(10.dp))
        PrimaryAction("Tiếp tục thanh toán", accepted && count <= available) {
            vm.place(box, count, plan, method, pickup)?.let(onOrder)
        }
    }) {
        PreviewNotice()
        CustomerCard { OrderBoxSummary(box)
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) { Text("Số lượng", style = MaterialTheme.typography.titleSmall); Text("Còn $available hộp", style = MaterialTheme.typography.bodySmall) }
                OutlinedButton(onClick = { count-- }, enabled = count > 1, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(48.dp)) { Text("−", fontSize = 22.sp) }
                Text(count.toString(), Modifier.padding(horizontal = 18.dp), fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { count++ }, enabled = count < available, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(48.dp)) { Text("+", fontSize = 22.sp) }
            }
            if (available == 0) Text("Hộp vừa hết. Quay lại khám phá để chọn hộp khác.", color = MaterialTheme.colorScheme.error)
        }
        CustomerCard {
            Text("Đến cửa hàng nhận hộp", style = MaterialTheme.typography.titleMedium)
            Text(orderTime(pickup.first) + " – " + orderTime(pickup.second).substringBefore(" ·"), style = MaterialTheme.typography.bodyLarge)
            Text(box.location?.address ?: box.district, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Khung giờ của đợt mẫu; không có dịch vụ giao hàng.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("Bạn muốn thanh toán thế nào?", style = MaterialTheme.typography.titleMedium)
        SelectionCard("Trả toàn bộ · ${money(total)}", "Đến nhận hộp, không cần trả thêm.", plan == PaymentPlan.FULL) { plan = PaymentPlan.FULL }
        SelectionCard("Đặt cọc ${policy.depositPercent}% · ${money(policy.deposit(total))}", "Còn ${money(total - policy.deposit(total))} trả tại cửa hàng khi nhận.", plan == PaymentPlan.DEPOSIT) { plan = PaymentPlan.DEPOSIT }
        Text("Phương thức thanh toán", style = MaterialTheme.typography.titleMedium)
        PaymentMethod.entries.forEach { option -> SelectionCard(option.label, "Qua cổng thanh toán của FoodRescue", method == option) { method = option } }
        CustomerCard {
            AmountRow("${money(box.price)} × $count hộp", total)
            AmountRow("Trả qua ứng dụng", payNow, true)
            AmountRow("Trả tại cửa hàng", total - payNow)
            Text("Thanh toán khoản trả trước qua FoodRescue. Phần còn lại, nếu có, trả tại cửa hàng khi nhận hộp.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        CustomerCard {
            Text("Trước khi đặt", style = MaterialTheme.typography.titleMedium)
            Text("• Giữ hộp ${policy.holdMinutes} phút kể từ khi tiếp tục thanh toán.\n• Hủy hợp lệ trước giờ nhận: hoàn toàn bộ khoản đã trả qua ứng dụng.\n• Món cụ thể được giữ bí mật; vui lòng xem kỹ thông tin dị ứng.", style = MaterialTheme.typography.bodyMedium)
            Text(box.allergens, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { policyOpen = !policyOpen }) { Text(if (policyOpen) "Thu gọn chính sách" else "Xem chính sách không đến nhận") }
            if (policyOpen) Text("Nếu được xác minh không đến nhận, khoản giữ lại là ${money(policy.deposit(total))} dù bạn trả toàn bộ hay đặt cọc. Phần vượt khoản này được hoàn; bạn không phải trả thêm. Bạn có thể phản ánh trong 48 giờ từ khi nhận thông báo. Tỷ lệ cọc ${policy.depositPercent}% đang là cấu hình minh họa, chưa phải chính sách phát hành.", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.Top) {
                Checkbox(accepted, { accepted = it })
                Text("Tôi đã xem giờ nhận, thông tin dị ứng và chính sách của đơn.", Modifier.padding(top = 10.dp).weight(1f), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
internal fun PaymentScreen(order: CustomerOrder, now: Long, vm: CustomerViewModel, onBack: () -> Unit, onDetails: () -> Unit) {
    var scenario by rememberSaveable(order.id) { mutableStateOf(PreviewPaymentResult.SUCCESS) }
    var scenariosOpen by rememberSaveable { mutableStateOf(false) }
    val awaiting = order.status == OrderStatus.WAITING && order.payment == PaymentStatus.NOT_STARTED
    val checking = order.payment == PaymentStatus.CHECKING
    val seconds = ((order.holdUntil - now).coerceAtLeast(0) / 1000)
    val title = when {
        order.payment == PaymentStatus.LATE -> "Khoản thanh toán đến muộn"
        order.status == OrderStatus.CONFIRMED -> "Hộp của bạn đã được xác nhận"
        order.status == OrderStatus.EXPIRED -> "Đã hết thời gian giữ hộp"
        checking -> "Đang xác nhận thanh toán"
        order.payment == PaymentStatus.FAILED -> "Thanh toán chưa thành công"
        order.status == OrderStatus.CANCELLED -> "Đã dừng lượt thanh toán"
        else -> "Hoàn tất thanh toán"
    }
    CustomerPage("Thanh toán", onBack, footer = {
        if (awaiting) PrimaryAction("Thanh toán thử ${money(order.payNow)}") { vm.pay(order.id, scenario) }
        else PrimaryAction("Xem chi tiết đơn", onClick = onDetails)
    }) {
        PreviewNotice("Thanh toán mô phỏng · Không nhập thẻ, không chuyển tiền thật")
        CustomerCard {
            if (checking) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
            else CatalogGlyph(if (order.status == OrderStatus.CONFIRMED) CatalogIcon.CHECK else CatalogIcon.RECEIPT, Modifier.size(32.dp), MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(when {
                order.payment == PaymentStatus.LATE -> "Đơn đã kết thúc nên không được khôi phục. Khoản đã nhận được chuyển xử lý hoàn tiền."
                order.status == OrderStatus.CONFIRMED -> "Bạn có thể xem mã nhận hàng và khung giờ trong chi tiết đơn."
                checking -> "Bạn có thể rời màn hình này và theo dõi ở Đơn hàng. Chưa có kết quả không có nghĩa là thất bại; đừng thanh toán thêm lần nữa."
                order.status == OrderStatus.EXPIRED -> "Hộp đã được giải phóng. Nếu giao dịch đến sau, hệ thống sẽ kiểm tra và xử lý hoàn tiền."
                order.status == OrderStatus.CANCELLED -> "Đơn chưa được xác nhận. Bạn có thể quay lại khám phá để đặt lượt mới."
                else -> "Hoàn tất khoản trả trước để xác nhận hộp."
            }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (order.status == OrderStatus.WAITING) Text("Giữ hộp còn ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
        CustomerCard {
            OrderBoxSummary(order.box, order.quantity)
            HorizontalDivider()
            AmountRow("Tổng đơn", order.total)
            AmountRow("${order.plan.label} qua ứng dụng", order.payNow, true)
            Text("${order.method.label} · Cổng thanh toán thử", style = MaterialTheme.typography.bodySmall)
            if (order.plan == PaymentPlan.DEPOSIT) AmountRow("Dự kiến trả khi nhận", order.total - order.payNow)
        }
        if (awaiting) {
            TextButton(onClick = { scenariosOpen = !scenariosOpen }) { Text("Tình huống thử thanh toán") }
            if (scenariosOpen) PreviewPaymentResult.entries.forEach { option ->
                SelectionCard(option.label, "Chỉ áp dụng cho bản trải nghiệm", option == scenario) { scenario = option }
            }
            TextButton(onClick = { vm.stopPayment(order.id) }, modifier = Modifier.fillMaxWidth()) { Text("Dừng thanh toán") }
        }
        if (checking) OutlinedButton(onClick = { vm.checkPending(order.id) }, modifier = Modifier.fillMaxWidth()) { Text("Nhận kết quả thử từ cổng thanh toán") }
        if (order.refundStatus != RefundStatus.NONE) CustomerCard { AmountRow("Khoản được xử lý hoàn", order.refundDue, true); Text(order.refundStatus.label) }
    }
}
