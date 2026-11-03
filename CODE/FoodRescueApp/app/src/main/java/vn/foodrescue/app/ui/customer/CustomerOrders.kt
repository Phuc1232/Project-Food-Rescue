package vn.foodrescue.app.ui.customer

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

internal val vietnamZone: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
internal fun orderTime(value: Long): String = Instant.ofEpochMilli(value).atZone(vietnamZone)
    .format(DateTimeFormatter.ofPattern("HH:mm · dd/MM/yyyy", Locale.forLanguageTag("vi-VN")))
internal fun money(value: Long): String = java.text.NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(value) + " đ"

/** Preview values only. The API must supply a versioned policy and authoritative timestamps. */
internal data class OrderPolicy(val depositPercent: Int = 30, val holdMinutes: Int = 10) {
    init { require(depositPercent in 1..100 && holdMinutes in 1..60) }
    fun deposit(total: Long): Long = BigDecimal.valueOf(total).multiply(BigDecimal.valueOf(depositPercent.toLong()))
        .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).longValueExact()
}
internal enum class PaymentPlan(val label: String) { FULL("Trả toàn bộ"), DEPOSIT("Đặt cọc") }
internal enum class PaymentMethod(val label: String) { CARD("Thẻ ngân hàng"), WALLET("Ví điện tử") }
internal enum class OrderStatus(val label: String) {
    WAITING("Chờ thanh toán"), CONFIRMED("Đã xác nhận"), COMPLETED("Đã nhận hộp"),
    CANCELLED("Đã hủy"), EXPIRED("Hết hạn thanh toán"), VERIFY_PICKUP("Chờ xác minh nhận hàng"), NO_SHOW("Không đến nhận")
}
internal enum class PaymentStatus(val label: String) {
    NOT_STARTED("Chưa thanh toán"), CHECKING("Đang xác nhận"), PAID("Đã thanh toán"),
    FAILED("Thanh toán thất bại"), CANCELLED("Đã dừng thanh toán"), LATE("Tiền đến sau khi đơn kết thúc")
}
internal enum class RefundStatus(val label: String) { NONE("Không có khoản hoàn"), PENDING("Đang xử lý hoàn tiền"), COMPLETED("Đã hoàn tiền") }
internal enum class SupportStatus(val label: String) { RECEIVED("Đã tiếp nhận"), REVIEWING("Đang xem xét"), RESOLVED("Đã giải quyết") }
internal enum class ComplaintReason(val label: String) {
    QUALITY("Chất lượng thực phẩm"), MISSING("Hộp không đúng cam kết"), STORE("Cửa hàng không thể giao hộp"),
    PAYMENT("Thanh toán hoặc hoàn tiền"), NO_SHOW("Phản ánh không đến nhận hàng"), OTHER("Vấn đề khác")
}
internal data class CustomerProfile(val name: String = "Khách trải nghiệm", val email: String = "khachhang@example.com", val phone: String = "")
internal data class Evidence(val uri: String, val name: String)
internal data class Complaint(val id: String, val reason: ComplaintReason, val content: String, val evidence: List<Evidence>,
    val createdAt: Long, val status: SupportStatus = SupportStatus.RECEIVED, val result: String? = null, val withinNoShowWindow: Boolean = false)
internal data class CustomerOrder(
    val id: String, val box: DemoBox, val quantity: Int, val plan: PaymentPlan, val method: PaymentMethod,
    val policy: OrderPolicy, val createdAt: Long, val holdUntil: Long, val pickupStart: Long, val pickupEnd: Long,
    val status: OrderStatus = OrderStatus.WAITING, val payment: PaymentStatus = PaymentStatus.NOT_STARTED,
    val paidInApp: Long = 0, val paidAtStore: Long = 0, val refundDue: Long = 0, val refunded: Long = 0,
    val refundStatus: RefundStatus = RefundStatus.NONE, val deduction: Long = 0,
    val cancellationReason: String? = null, val cancellationPending: Boolean = false,
    val receivedNoShowAt: Long? = null, val complaint: Complaint? = null,
) {
    val total: Long get() = box.price.toLong() * quantity
    val deposit: Long get() = policy.deposit(total)
    val payNow: Long get() = if (plan == PaymentPlan.FULL) total else deposit
    val remainingAtStore: Long get() = if (status == OrderStatus.CONFIRMED) (total - paidInApp - paidAtStore).coerceAtLeast(0) else 0
    val pickupCode: String? get() = if (status == OrderStatus.CONFIRMED && payment == PaymentStatus.PAID) id.takeLast(6).uppercase(Locale.ROOT) else null
    val active: Boolean get() = status in setOf(OrderStatus.WAITING, OrderStatus.CONFIRMED, OrderStatus.VERIFY_PICKUP)
    val holdsStock: Boolean get() = status !in setOf(OrderStatus.CANCELLED, OrderStatus.EXPIRED)
    val noShowDeadline: Long? get() = receivedNoShowAt?.plus(48 * 60 * 60 * 1000L)
}
internal data class CustomerSnapshot(val version: Int = 1, val profile: CustomerProfile = CustomerProfile(), val orders: List<CustomerOrder> = emptyList())

/** Future demo session; never present a past pickup window as available. */
internal fun previewPickup(box: DemoBox, now: Long): Pair<Long, Long> {
    val today = Instant.ofEpochMilli(now).atZone(vietnamZone).toLocalDate()
    fun start(day: LocalDate) = day.atTime(box.pickupStartHour, 0).atZone(vietnamZone).toInstant().toEpochMilli()
    val date = if (start(today) <= now + 10 * 60_000L) today.plusDays(1) else today
    val from = start(date)
    return from to (from + 2 * 60 * 60_000L)
}

/** Pure state machine used by the debug preview only. Production transitions belong to the backend. */
internal class CustomerOrderEngine(initial: CustomerSnapshot = CustomerSnapshot()) {
    var snapshot = initial
        private set
    fun available(box: DemoBox, pickupStart: Long): Int = (box.remaining - snapshot.orders
        .filter { it.box.id == box.id && it.pickupStart == pickupStart && it.holdsStock }.sumOf { it.quantity }).coerceAtLeast(0)
    fun refresh(now: Long) {
        snapshot = snapshot.copy(orders = snapshot.orders.map {
            when {
                it.status == OrderStatus.WAITING && now >= it.holdUntil -> it.copy(status = OrderStatus.EXPIRED)
                it.status == OrderStatus.CONFIRMED && now >= it.pickupEnd -> it.copy(status = OrderStatus.VERIFY_PICKUP)
                else -> it
            }
        })
    }
    fun place(box: DemoBox, quantity: Int, plan: PaymentPlan, method: PaymentMethod, pickup: Pair<Long, Long>, now: Long): CustomerOrder {
        refresh(now)
        require(now < pickup.first) { "Khung giờ nhận đã bắt đầu. Vui lòng chọn đợt bán khác." }
        require(quantity in 1..available(box, pickup.first)) { "Không còn đủ hộp cho số lượng bạn chọn." }
        require(snapshot.orders.none { it.box.id == box.id && it.status == OrderStatus.WAITING }) { "Bạn đang có đơn chờ thanh toán tại cửa hàng này. Hãy mở tab Đơn hàng." }
        val policy = OrderPolicy()
        val order = CustomerOrder(UUID.randomUUID().toString(), box, quantity, plan, method, policy, now,
            minOf(now + policy.holdMinutes * 60_000L, pickup.first), pickup.first, pickup.second)
        snapshot = snapshot.copy(orders = listOf(order) + snapshot.orders)
        return order
    }
    private fun change(id: String, action: (CustomerOrder) -> CustomerOrder) {
        require(snapshot.orders.any { it.id == id }) { "Không tìm thấy đơn hàng." }
        snapshot = snapshot.copy(orders = snapshot.orders.map { if (it.id == id) action(it) else it })
    }
    fun beginPayment(id: String, now: Long) {
        refresh(now)
        change(id) {
            require(it.status == OrderStatus.WAITING && it.payment == PaymentStatus.NOT_STARTED) { "Đơn này không thể tạo thêm lượt thanh toán." }
            it.copy(payment = PaymentStatus.CHECKING)
        }
    }
    fun paymentResult(id: String, success: Boolean, now: Long) {
        refresh(now)
        change(id) {
            // One simulated provider reference per order; repeated callbacks are idempotent.
            if (it.paidInApp > 0 || it.payment != PaymentStatus.CHECKING) it
            else if (success && it.status == OrderStatus.WAITING) it.copy(status = OrderStatus.CONFIRMED, payment = PaymentStatus.PAID, paidInApp = it.payNow)
            else if (success) it.copy(payment = PaymentStatus.LATE, paidInApp = it.payNow, refundDue = it.payNow, refundStatus = RefundStatus.PENDING)
            else if (it.status == OrderStatus.WAITING) it.copy(status = OrderStatus.CANCELLED, payment = PaymentStatus.FAILED)
            else it.copy(payment = PaymentStatus.FAILED)
        }
    }
    fun stopPayment(id: String, now: Long) {
        refresh(now)
        change(id) {
            require(it.status == OrderStatus.WAITING && it.payment == PaymentStatus.NOT_STARTED) { "Đang xác nhận giao dịch. Hãy chờ kết quả trước khi hủy." }
            it.copy(status = OrderStatus.CANCELLED, payment = PaymentStatus.CANCELLED)
        }
    }
    fun requestCancellation(id: String, reason: String, now: Long) {
        require(reason.isNotBlank()) { "Vui lòng chọn lý do hủy." }
        refresh(now)
        change(id) {
            require(it.status == OrderStatus.CONFIRMED && !it.cancellationPending) { "Đơn này không thể gửi thêm yêu cầu hủy." }
            if (now < it.pickupStart) it.copy(status = OrderStatus.CANCELLED, cancellationReason = reason,
                refundDue = it.paidInApp, refundStatus = if (it.paidInApp > 0) RefundStatus.PENDING else RefundStatus.NONE)
            else it.copy(cancellationReason = reason, cancellationPending = true)
        }
    }
    fun complain(id: String, reason: ComplaintReason, content: String, evidence: List<Evidence>, now: Long) {
        require(content.trim().length in 20..2000) { "Nội dung cần từ 20 đến 2.000 ký tự." }
        require(evidence.size <= 5) { "Bạn có thể đính kèm tối đa 5 ảnh." }
        change(id) {
            require(it.status != OrderStatus.WAITING) { "Hãy chờ kết quả thanh toán trước khi gửi khiếu nại cho đơn này." }
            require(it.complaint == null) { "Đơn này đã có hồ sơ hỗ trợ. Bạn có thể theo dõi trong chi tiết đơn." }
            require(reason != ComplaintReason.NO_SHOW || it.status == OrderStatus.NO_SHOW) { "Đơn chưa có kết luận không đến nhận hàng." }
            it.copy(complaint = Complaint(UUID.randomUUID().toString(), reason, content.trim(), evidence, now,
                withinNoShowWindow = reason == ComplaintReason.NO_SHOW && (it.noShowDeadline == null || now <= it.noShowDeadline!!)))
        }
    }
    fun updateProfile(name: String, phone: String) {
        require(name.trim().length in 2..80) { "Họ tên cần từ 2 đến 80 ký tự." }
        require(phone.isBlank() || Regex("^(0[0-9]{9}|\\+84[0-9]{9})$").matches(phone.trim())) { "Nhập số điện thoại Việt Nam gồm 10 số hoặc bắt đầu bằng +84." }
        snapshot = snapshot.copy(profile = snapshot.profile.copy(name = name.trim(), phone = phone.trim()))
    }
    // Explicit preview controls. Never triggered by the customer's normal action buttons.
    fun finishRefund(id: String) = change(id) {
        require(it.refundStatus == RefundStatus.PENDING)
        it.copy(refunded = it.refundDue, refundStatus = RefundStatus.COMPLETED)
    }
    fun resolveComplaint(id: String, accepted: Boolean) = change(id) {
        val complaint = requireNotNull(it.complaint)
        require(complaint.status != SupportStatus.RESOLVED) { "Hồ sơ này đã có kết quả xử lý." }
        val completed = complaint.copy(status = SupportStatus.RESOLVED, result = if (accepted)
            "Đã chấp thuận phản ánh và yêu cầu hoàn phần tiền ứng dụng chưa hoàn. Đây là kết quả mô phỏng."
            else "Đã xem xét và giữ nguyên kết quả xử lý đơn. Đây là kết quả mô phỏng.")
        if (accepted) it.copy(complaint = completed, deduction = 0, refundDue = it.paidInApp,
            refundStatus = if (it.paidInApp > it.refunded) RefundStatus.PENDING else it.refundStatus,
            status = if (it.status != OrderStatus.COMPLETED) OrderStatus.CANCELLED else it.status, cancellationPending = false)
        else it.copy(complaint = completed)
    }
    fun clear() { snapshot = CustomerSnapshot() }
    fun resolveCancellation(id: String, accepted: Boolean) = change(id) {
        require(it.cancellationPending && it.status == OrderStatus.CONFIRMED)
        if (accepted) it.copy(status = OrderStatus.CANCELLED, cancellationPending = false,
            refundDue = it.paidInApp, refundStatus = if (it.paidInApp > 0) RefundStatus.PENDING else RefundStatus.NONE)
        else it.copy(cancellationPending = false, cancellationReason = "Yêu cầu hủy không được chấp thuận trong tình huống thử. Đơn tiếp tục được thực hiện.")
    }
    fun expirePreview(id: String) = change(id) { it.copy(holdUntil = it.createdAt) }
    fun addExamples(now: Long) {
        if (snapshot.orders.any { it.id.startsWith("sample-") }) return
        fun example(id: String, status: OrderStatus): CustomerOrder {
            val box = demoBoxes.first()
            return CustomerOrder("sample-$id", box, 2, PaymentPlan.FULL, PaymentMethod.CARD, OrderPolicy(),
                now - 2 * 86_400_000L, now - 2 * 86_400_000L + 600_000, now - 4 * 3_600_000L, now - 2 * 3_600_000L,
                status, PaymentStatus.PAID, paidInApp = box.price * 2L)
        }
        val noShow = example("NS4826", OrderStatus.NO_SHOW).let { it.copy(deduction = it.deposit,
            refundDue = it.total - it.deposit, refundStatus = RefundStatus.PENDING, receivedNoShowAt = now) }
        val cancelled = example("HU4827", OrderStatus.CANCELLED).let { it.copy(cancellationReason = "Thay đổi kế hoạch", refundDue = it.paidInApp, refundStatus = RefundStatus.COMPLETED, refunded = it.paidInApp) }
        snapshot = snapshot.copy(orders = snapshot.orders + listOf(example("DN4825", OrderStatus.COMPLETED), noShow, cancelled))
    }
}
