package vn.foodrescue.app.ui.customer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun OrdersScreen(orders: List<CustomerOrder>, onOrder: (String) -> Unit, onExplore: () -> Unit, onExamples: () -> Unit) {
    var history by rememberSaveable { mutableStateOf(false) }
    val shown = orders.filter { if (history) !it.active else it.active }
    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Text("Đơn hàng", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) }
        item { Text("Giờ nhận, thanh toán và hỗ trợ — theo dõi ở một nơi.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(!history, { history = false }, label = { Text("Đang thực hiện") })
            FilterChip(history, { history = true }, label = { Text("Lịch sử") })
        } }
        if (shown.isEmpty()) item { CatalogEmpty(if (history) "Chưa có lịch sử đơn hàng" else "Chưa có đơn đang thực hiện",
            "Chọn một hộp phù hợp, đặt trước và đến cửa hàng nhận đúng giờ.", "Khám phá hộp", onExplore) }
        items(shown, key = { it.id }) { order ->
            CustomerCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(order.status.label, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    Text("#${order.id.takeLast(6).uppercase()}", style = MaterialTheme.typography.labelSmall)
                }
                OrderBoxSummary(order.box, order.quantity)
                Text("Nhận lúc ${orderTime(order.pickupStart)}", style = MaterialTheme.typography.bodySmall)
                if (order.refundStatus != RefundStatus.NONE) Text(order.refundStatus.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                AmountRow("Tổng đơn", order.total)
                OutlinedButton(onClick = { onOrder(order.id) }, modifier = Modifier.fillMaxWidth()) { Text("Xem đơn hàng") }
            }
        }
        item { PreviewNotice("Đơn thử được lưu riêng trên điện thoại. Không giữ hàng hay thu tiền thật.") }
        item { TextButton(onClick = { onExamples(); history = true }, modifier = Modifier.fillMaxWidth()) { Text("Thêm đơn mẫu để xem lịch sử và hỗ trợ") } }
    }
}

@Composable
internal fun OrderDetailScreen(order: CustomerOrder, now: Long, vm: CustomerViewModel, onBack: () -> Unit,
    onPay: () -> Unit, onCancel: () -> Unit, onSupport: () -> Unit) {
    var previewTools by rememberSaveable { mutableStateOf(false) }
    CustomerPage("Chi tiết đơn hàng", onBack) {
        PreviewNotice()
        Text(order.status.label, style = MaterialTheme.typography.headlineMedium)
        Text("Đơn #${order.id.takeLast(6).uppercase()} · ${orderTime(order.createdAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CustomerCard { OrderBoxSummary(order.box, order.quantity)
            HorizontalDivider()
            Text("Khung giờ nhận", style = MaterialTheme.typography.titleSmall)
            Text("${orderTime(order.pickupStart)} – ${orderTime(order.pickupEnd).substringBefore(" ·")}")
            Text(order.box.location?.address ?: order.box.district, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        order.pickupCode?.let { code -> CustomerCard {
            Text("Mã nhận hàng", style = MaterialTheme.typography.titleMedium)
            Text(code, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp, color = MaterialTheme.colorScheme.primary)
            Text("Đưa mã này cho nhân viên khi nhận đủ hộp. Mã trong bản thử không dùng tại cửa hàng thật.", style = MaterialTheme.typography.bodySmall)
            if (order.remainingAtStore > 0) AmountRow("Trả thêm khi nhận", order.remainingAtStore, true)
        } }
        if (order.status == OrderStatus.WAITING) {
            val seconds = ((order.holdUntil - now).coerceAtLeast(0) / 1000)
            Text("Thời gian giữ hộp: ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}", color = MaterialTheme.colorScheme.primary)
            PrimaryAction(if (order.payment == PaymentStatus.CHECKING) "Theo dõi thanh toán" else "Tiếp tục thanh toán", onClick = onPay)
        }
        if (order.payment == PaymentStatus.CHECKING && order.status != OrderStatus.WAITING) {
            Text("Đơn đã hết hiệu lực nhưng giao dịch vẫn đang được đối chiếu. Tiền đến muộn sẽ được xử lý hoàn; đơn không tự khôi phục.", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onPay, modifier = Modifier.fillMaxWidth()) { Text("Theo dõi giao dịch") }
        }
        CustomerCard {
            Text("Thanh toán", style = MaterialTheme.typography.titleMedium)
            AmountRow("Tổng giá trị đơn", order.total)
            AmountRow("Đã trả qua ứng dụng", order.paidInApp)
            AmountRow("Đã trả tại cửa hàng", order.paidAtStore)
            AmountRow("Còn phải trả tại cửa hàng", order.remainingAtStore, true)
            Text(order.payment.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (order.deduction > 0) { HorizontalDivider(); AmountRow("Khoản giữ lại theo chính sách", order.deduction) }
            if (order.refundStatus != RefundStatus.NONE) {
                HorizontalDivider(); AmountRow("Tổng khoản được hoàn", order.refundDue)
                AmountRow("Đã hoàn", order.refunded)
                AmountRow("Còn chờ hoàn", (order.refundDue - order.refunded).coerceAtLeast(0))
                Text(order.refundStatus.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (order.cancellationReason != null) CustomerCard {
            Text("Yêu cầu hủy đơn", style = MaterialTheme.typography.titleMedium)
            Text(order.cancellationReason)
            Text(when { order.cancellationPending -> "Đang xem xét vì khung giờ nhận đã bắt đầu. Đơn chưa được hủy; bạn vẫn cần theo dõi kết quả hoặc nhận đúng giờ."
                order.status == OrderStatus.CANCELLED -> "Đơn đã hủy. Khoản hoàn được theo dõi riêng ở phần Thanh toán."
                else -> "Yêu cầu hủy đã được xử lý. Xem trạng thái đơn hiện tại ở đầu trang." }, style = MaterialTheme.typography.bodyMedium)
        }
        if (order.status == OrderStatus.VERIFY_PICKUP) PreviewNotice("Đã hết giờ nhận. Cửa hàng cần xác minh việc bàn giao trước khi kết luận; chưa tự khấu trừ tiền.")
        if (order.status == OrderStatus.NO_SHOW) CustomerCard {
            Text("Thông báo không đến nhận", style = MaterialTheme.typography.titleMedium)
            Text("Khoản giữ lại bằng tiền cọc tiêu chuẩn. Bạn không phải trả thêm phần còn lại.")
            Text(order.noShowDeadline?.let { "Thời hạn phản ánh: ${orderTime(it)}. Tính từ khi bạn nhận được thông báo." }
                ?: "Chưa xác định thời điểm nhận thông báo; chưa tự kết thúc thời hạn phản ánh.", style = MaterialTheme.typography.bodySmall)
        }
        order.complaint?.let { complaint -> CustomerCard {
            Text("Hồ sơ hỗ trợ #${complaint.id.takeLast(6).uppercase()}", style = MaterialTheme.typography.titleMedium)
            Text(complaint.status.label, color = MaterialTheme.colorScheme.primary)
            Text(complaint.reason.label, style = MaterialTheme.typography.titleSmall)
            Text(complaint.content)
            if (complaint.evidence.isNotEmpty()) Text("${complaint.evidence.size} ảnh đã đính kèm · Chỉ lưu trong bản thử", style = MaterialTheme.typography.bodySmall)
            Text(complaint.result ?: "Đã nhận nội dung. Việc tiếp nhận chưa đồng nghĩa yêu cầu hoàn tiền được chấp thuận.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (complaint.withinNoShowWindow && complaint.status != SupportStatus.RESOLVED) Text("Khoản bù đắp tiếp tục được giữ trong thời gian xem xét phản ánh.", style = MaterialTheme.typography.bodySmall)
        } }
        if (order.status == OrderStatus.CONFIRMED && !order.cancellationPending) OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Yêu cầu hủy đơn") }
        if (order.status != OrderStatus.WAITING && order.complaint == null) OutlinedButton(onClick = onSupport, modifier = Modifier.fillMaxWidth()) { Text("Gửi khiếu nại / yêu cầu hỗ trợ") }
        TextButton(onClick = { previewTools = !previewTools }) { Text("Công cụ kiểm tra bản trải nghiệm") }
        if (previewTools) CustomerCard {
            Text("Các thao tác dưới đây chỉ giả lập phản hồi xử lý; người mua không có quyền tự duyệt giao dịch thật.", style = MaterialTheme.typography.bodySmall)
            if (order.refundStatus == RefundStatus.PENDING) TextButton(onClick = { vm.finishRefund(order.id) }) { Text("Giả lập hoàn tiền thành công") }
            if (order.complaint != null && order.complaint.status != SupportStatus.RESOLVED) {
                TextButton(onClick = { vm.resolveComplaint(order.id, true) }) { Text("Giả lập chấp thuận phản ánh") }
                TextButton(onClick = { vm.resolveComplaint(order.id, false) }) { Text("Giả lập giữ nguyên kết quả") }
            }
            if (order.cancellationPending) {
                TextButton(onClick = { vm.resolveCancellation(order.id, true) }) { Text("Giả lập chấp thuận hủy và hoàn") }
                TextButton(onClick = { vm.resolveCancellation(order.id, false) }) { Text("Giả lập không chấp thuận hủy") }
            }
        }
    }
}

@Composable
internal fun CancelOrderScreen(order: CustomerOrder, now: Long, onBack: () -> Unit, onSubmit: (String) -> Unit) {
    var reason by rememberSaveable { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    val automatic = now < order.pickupStart
    CustomerPage("Yêu cầu hủy đơn", onBack, footer = {
        PrimaryAction("Gửi yêu cầu hủy", reason.isNotBlank()) { confirm = true }
    }) {
        PreviewNotice()
        OrderBoxSummary(order.box, order.quantity)
        CustomerCard {
            Text(if (automatic) "Hủy trước giờ nhận" else "Khung giờ nhận đã bắt đầu", style = MaterialTheme.typography.titleMedium)
            Text(if (automatic) "Yêu cầu hợp lệ trước ${orderTime(order.pickupStart)} được hoàn toàn bộ khoản đã thanh toán qua ứng dụng."
                else "Yêu cầu cần được xem xét. Chưa thể cam kết mức hoàn hoặc xác nhận hủy đơn.")
            AmountRow("Đã trả qua ứng dụng", order.paidInApp)
            if (automatic) AmountRow("Dự kiến hoàn", (order.paidInApp - order.refunded).coerceAtLeast(0), true)
            Text("Trạng thái hoàn tiền được cập nhật riêng trong chi tiết đơn.", style = MaterialTheme.typography.bodySmall)
        }
        Text("Lý do hủy", style = MaterialTheme.typography.titleMedium)
        listOf("Thay đổi kế hoạch", "Không thể đến đúng giờ", "Đặt nhầm hộp hoặc số lượng", "Cửa hàng không thể giao hộp", "Lý do khác").forEach {
            SelectionCard(it, "", reason == it) { reason = it }
        }
        Text("Nếu cửa hàng không thể giao hộp, bạn có thể gửi bằng chứng ở mục hỗ trợ. Không tự áp dụng phí cho khách khi lỗi thuộc cửa hàng.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Gửi yêu cầu hủy đơn?") },
        text = { Text(if (automatic) "Hệ thống sẽ kiểm tra lại thời điểm gửi và điều kiện hủy. Tiền hoàn chưa được nhận ngay khi đơn hủy." else "Đơn chỉ được hủy khi có kết quả chấp thuận. Bạn có thể theo dõi trong chi tiết đơn.") },
        confirmButton = { TextButton(onClick = { confirm = false; onSubmit(reason) }) { Text("Gửi yêu cầu") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Giữ đơn") } })
}
