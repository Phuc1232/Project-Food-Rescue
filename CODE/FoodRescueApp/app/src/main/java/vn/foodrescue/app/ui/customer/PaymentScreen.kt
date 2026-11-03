package vn.foodrescue.app.ui.customer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PaymentScreen(order: CustomerOrder, now: Long, vm: CustomerViewModel, onBack: () -> Unit, onDetails: () -> Unit) {
    var scenario by rememberSaveable(order.id) { mutableStateOf(PreviewPaymentResult.SUCCESS) }
    var scenariosOpen by rememberSaveable { mutableStateOf(false) }
    var stopOpen by remember { mutableStateOf(false) }
    val awaiting = order.status == OrderStatus.WAITING && order.payment == PaymentStatus.NOT_STARTED
    val checking = order.payment == PaymentStatus.CHECKING
    val success = order.status == OrderStatus.CONFIRMED && order.payment == PaymentStatus.PAID
    val failed = order.payment == PaymentStatus.FAILED
    val late = order.payment == PaymentStatus.LATE
    val seconds = ((order.holdUntil - now).coerceAtLeast(0) / 1000)
    val title = when {
        late -> "Khoản tiền cần hoàn lại"
        checking -> "Đang xác nhận thanh toán"
        success && order.plan == PaymentPlan.DEPOSIT -> "Đặt cọc thành công"
        success -> "Thanh toán thành công"
        failed -> "Thanh toán chưa thành công"
        order.status == OrderStatus.EXPIRED -> "Đã hết thời gian giữ hộp"
        order.status == OrderStatus.CANCELLED -> "Đã dừng thanh toán"
        else -> "Sẵn sàng thanh toán"
    }
    val description = when {
        late -> "Đơn đã kết thúc. Khoản đã nhận đang được xử lý hoàn lại; không tạo đơn mới từ giao dịch này."
        checking -> "Bạn có thể theo dõi kết quả trong Đơn hàng. Vui lòng chờ xác nhận trước khi thanh toán thêm."
        success && order.plan == PaymentPlan.DEPOSIT -> "Hộp đã được xác nhận. Phần còn lại được trả tại cửa hàng khi nhận."
        success -> "Hộp đã được xác nhận. Bạn chỉ cần đến cửa hàng trong khung giờ đã chọn."
        failed -> "Đơn chưa được xác nhận. Bạn có thể quay lại khám phá và đặt lượt mới."
        order.status == OrderStatus.EXPIRED -> "Hộp đã được giải phóng. Giao dịch chưa rõ kết quả sẽ được kiểm tra riêng."
        order.status == OrderStatus.CANCELLED -> "Lượt đặt này đã dừng và hộp được giải phóng."
        else -> "Kiểm tra số tiền và phương thức trước khi tiếp tục."
    }
    val accent = if (failed) MaterialTheme.colorScheme.error else if (late) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
    CustomerPage("Thanh toán", onBack, footer = {
        if (awaiting) PaymentFooter(order.payNow, "Thanh toán thử", remaining = order.total - order.payNow) { vm.pay(order.id, scenario) }
        else PrimaryAction(if (success) "Xem mã nhận hàng" else if (checking) "Theo dõi đơn hàng" else "Xem chi tiết đơn", onClick = onDetails)
    }) {
        PaymentPreviewLabel()
        Column(Modifier.fillMaxWidth().animateContentSize().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(color = accent.copy(alpha = .08f), shape = CircleShape, modifier = Modifier.size(64.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    if (checking) CircularProgressIndicator(Modifier.size(28.dp), color = accent, strokeWidth = 2.5.dp)
                    else AnimatedContent(targetState = if (success) CatalogIcon.CHECK else if (failed) CatalogIcon.CLOSE else if (awaiting) CatalogIcon.RECEIPT else CatalogIcon.CLOCK, label = "paymentStatus") {
                        CatalogGlyph(it, Modifier.size(30.dp), accent)
                    }
                }
            }
            Text(title, fontSize = 22.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(description, fontSize = 13.sp, lineHeight = 20.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (awaiting) Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(50)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    CatalogGlyph(CatalogIcon.CLOCK, Modifier.size(16.dp), MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Giữ hộp ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}", fontSize = 12.sp,
                        fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        CustomerCard {
            Text(if (success || late) "Đã trả qua ứng dụng" else "Khoản thanh toán", fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(money(if (success || late) order.paidInApp else order.payNow), fontSize = 30.sp, lineHeight = 36.sp,
                fontWeight = FontWeight.Bold, color = accent)
            Text("${order.plan.label} · Đơn #${order.id.takeLast(6).uppercase()}", fontSize = 12.sp, lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                PaymentMethodBadge(order.method)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(order.method.label, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("Giao dịch mô phỏng", fontSize = 11.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            AmountRow("Tổng giá trị đơn", order.total)
            if (success) AmountRow("Còn trả khi nhận", order.remainingAtStore, order.remainingAtStore > 0)
            else if (awaiting || checking) AmountRow("Dự kiến trả khi nhận", order.total - order.payNow)
        }
        if (success) CustomerCard {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                CatalogGlyph(CatalogIcon.BAG, Modifier.size(22.dp), MaterialTheme.colorScheme.primary)
                Text("Hẹn bạn tại cửa hàng", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(order.box.store, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
            Text("${orderTime(order.pickupStart)} – ${orderTime(order.pickupEnd).substringBefore(" ·")}", fontSize = 13.sp, lineHeight = 19.sp)
            Text("Mở mã nhận hàng khi đến nơi. Mã của bản thử không dùng tại cửa hàng thật.", fontSize = 12.sp,
                lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (order.refundStatus != RefundStatus.NONE) CustomerCard {
            PaymentSectionTitle("Thông tin hoàn tiền")
            AmountRow("Khoản được hoàn", order.refundDue, true)
            Text(order.refundStatus.label, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            Text("Hủy đơn và hoàn tiền có trạng thái riêng. Tiền chỉ được ghi nhận đã hoàn sau khi có kết quả xác nhận.",
                fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (awaiting) {
            Text("Trong bản thử, nút thanh toán mô phỏng kết quả và không mở ứng dụng ngân hàng hoặc ví của bạn.",
                fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { stopOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("Dừng thanh toán", fontSize = 13.sp) }
        }
        if (awaiting || checking) TextButton(onClick = { scenariosOpen = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Tùy chọn bản thử", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (stopOpen) AlertDialog(onDismissRequest = { stopOpen = false }, title = { Text("Dừng thanh toán?") },
        text = { Text("Đơn chưa được xác nhận. Dừng thanh toán sẽ giải phóng số hộp đang giữ.") },
        confirmButton = { TextButton(onClick = { stopOpen = false; vm.stopPayment(order.id) }) { Text("Dừng thanh toán") } },
        dismissButton = { TextButton(onClick = { stopOpen = false }) { Text("Tiếp tục giữ hộp") } })
    if (scenariosOpen) ModalBottomSheet(onDismissRequest = { scenariosOpen = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Tình huống thử", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Chỉ thay đổi kết quả mô phỏng, không tạo giao dịch thật.", fontSize = 12.sp, lineHeight = 18.sp)
            if (awaiting) Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PreviewPaymentResult.entries.forEach { option -> SelectionCard(option.label, "", option == scenario) { scenario = option; scenariosOpen = false } }
            }
            if (checking) PrimaryAction("Nhận kết quả thử") { vm.checkPending(order.id); scenariosOpen = false }
        }
    }
}
