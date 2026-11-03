package vn.foodrescue.app.ui.customer

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class CustomerOrdersTest {
    private val now = 1_800_000_000_000L
    private val box = demoBoxes.first()
    private val slot = now + 3_600_000L to now + 10_800_000L
    private fun newOrder(engine: CustomerOrderEngine, plan: PaymentPlan = PaymentPlan.FULL) =
        engine.place(box, 2, plan, PaymentMethod.CARD, slot, now)
    private fun paid(engine: CustomerOrderEngine, plan: PaymentPlan = PaymentPlan.FULL): CustomerOrder {
        val order = newOrder(engine, plan)
        engine.beginPayment(order.id, now + 1000)
        engine.paymentResult(order.id, true, now + 2000)
        return engine.snapshot.orders.first()
    }
    @Test fun `deposit and full payment expose matching balances and pickup eligibility`() {
        for (plan in PaymentPlan.entries) {
            val engine = CustomerOrderEngine()
            val order = newOrder(engine, plan)
            assertNull(order.pickupCode)
            engine.beginPayment(order.id, now + 1000)
            assertNull(engine.snapshot.orders.first().pickupCode)
            engine.paymentResult(order.id, true, now + 2000)
            val confirmed = engine.snapshot.orders.first()
            assertNotNull(confirmed.pickupCode)
            assertEquals(78_000L, confirmed.total)
            assertEquals(if (plan == PaymentPlan.FULL) 78_000L else 23_400L, confirmed.paidInApp)
            assertEquals(confirmed.total, confirmed.paidInApp + confirmed.remainingAtStore)
        }
    }
    @Test fun `hold expires at exact deadline and a late success never revives the order`() {
        val engine = CustomerOrderEngine(); val order = newOrder(engine)
        engine.beginPayment(order.id, now)
        engine.refresh(order.holdUntil - 1)
        assertEquals(OrderStatus.WAITING, engine.snapshot.orders.first().status)
        engine.paymentResult(order.id, true, order.holdUntil)
        val late = engine.snapshot.orders.first()
        assertEquals(OrderStatus.EXPIRED, late.status)
        assertEquals(PaymentStatus.LATE, late.payment)
        assertEquals(late.payNow, late.refundDue)
        assertEquals(0L, late.remainingAtStore)
        assertNull(late.pickupCode)
        engine.paymentResult(order.id, true, order.holdUntil + 1000)
        assertEquals(late, engine.snapshot.orders.first())
    }
    @Test fun `unknown payment cannot be retried or cancelled as if it had failed`() {
        val engine = CustomerOrderEngine(); val order = newOrder(engine)
        engine.beginPayment(order.id, now)
        engine.refresh(now + 30_000)
        assertEquals(PaymentStatus.CHECKING, engine.snapshot.orders.first().payment)
        assertThrows(IllegalArgumentException::class.java) { engine.beginPayment(order.id, now + 30_000) }
        assertThrows(IllegalArgumentException::class.java) { engine.stopPayment(order.id, now + 30_000) }
        assertEquals(2, engine.available(box, slot.first))
    }
    @Test fun `definitive failure releases stock but successful callback repeats do not add money`() {
        val failed = CustomerOrderEngine(); val order = newOrder(failed)
        failed.beginPayment(order.id, now); failed.paymentResult(order.id, false, now + 1000)
        assertEquals(4, failed.available(box, slot.first))
        assertEquals(OrderStatus.CANCELLED, failed.snapshot.orders.first().status)
        val engine = CustomerOrderEngine(); val success = paid(engine)
        engine.paymentResult(success.id, true, now + 5000)
        engine.paymentResult(success.id, false, now + 6000)
        assertEquals(success, engine.snapshot.orders.first())
    }
    @Test fun `stock cannot be overbooked and a duplicate pending order is rejected`() {
        val engine = CustomerOrderEngine(); newOrder(engine)
        assertThrows(IllegalArgumentException::class.java) { newOrder(engine) }
        val restored = CustomerOrderEngine(engine.snapshot.copy(orders = engine.snapshot.orders.map { it.copy(status = OrderStatus.CONFIRMED) }))
        assertThrows(IllegalArgumentException::class.java) { restored.place(box, 3, PaymentPlan.FULL, PaymentMethod.CARD, slot, now) }
        assertEquals(4, restored.available(box, slot.first + 86_400_000L))
    }
    @Test fun `cancellation before pickup refunds only money received and zeroes balance`() {
        val engine = CustomerOrderEngine(); val order = paid(engine, PaymentPlan.DEPOSIT)
        engine.requestCancellation(order.id, "Thay đổi kế hoạch", slot.first - 1)
        val cancelled = engine.snapshot.orders.first()
        assertEquals(OrderStatus.CANCELLED, cancelled.status)
        assertEquals(23_400L, cancelled.refundDue)
        assertEquals(0L, cancelled.refunded)
        assertEquals(0L, cancelled.remainingAtStore)
        assertNull(cancelled.pickupCode)
        engine.finishRefund(order.id)
        assertEquals(23_400L, engine.snapshot.orders.first().refunded)
        assertThrows(IllegalArgumentException::class.java) { engine.finishRefund(order.id) }
    }
    @Test fun `cancellation exactly at pickup requires review instead of automatic full refund`() {
        val engine = CustomerOrderEngine(); val order = paid(engine)
        engine.requestCancellation(order.id, "Không đến kịp", slot.first)
        val pending = engine.snapshot.orders.first()
        assertTrue(pending.cancellationPending)
        assertEquals(OrderStatus.CONFIRMED, pending.status)
        assertEquals(0L, pending.refundDue)
        engine.resolveCancellation(order.id, false)
        assertEquals(OrderStatus.CONFIRMED, engine.snapshot.orders.first().status)
    }
    @Test fun `passing pickup end requires verification without automatic penalty`() {
        val engine = CustomerOrderEngine(); val order = paid(engine)
        engine.refresh(order.pickupEnd)
        val overdue = engine.snapshot.orders.first()
        assertEquals(OrderStatus.VERIFY_PICKUP, overdue.status)
        assertEquals(0L, overdue.deduction)
        assertEquals(0L, overdue.remainingAtStore)
        assertNull(overdue.pickupCode)
    }
    @Test fun `no show refund and retained deposit balance and complaint uses receipt deadline`() {
        val engine = CustomerOrderEngine(); engine.addExamples(now)
        val order = engine.snapshot.orders.first { it.status == OrderStatus.NO_SHOW }
        assertEquals(order.paidInApp, order.deduction + order.refundDue)
        assertEquals(0L, order.remainingAtStore)
        val content = "Tôi đến đúng giờ nhưng cửa hàng đã đóng cửa."
        engine.complain(order.id, ComplaintReason.NO_SHOW, content, emptyList(), order.noShowDeadline!!)
        assertTrue(engine.snapshot.orders.first { it.id == order.id }.complaint!!.withinNoShowWindow)
        assertThrows(IllegalArgumentException::class.java) { engine.complain(order.id, ComplaintReason.NO_SHOW, content, emptyList(), now) }
        engine.resolveComplaint(order.id, true)
        val resolved = engine.snapshot.orders.first { it.id == order.id }
        assertEquals(0L, resolved.deduction)
        assertEquals(resolved.paidInApp, resolved.refundDue)
        assertEquals(OrderStatus.CANCELLED, resolved.status)
    }
    @Test fun `late no show complaint is accepted for review without promising the protected window`() {
        val engine = CustomerOrderEngine(); engine.addExamples(now)
        val order = engine.snapshot.orders.first { it.status == OrderStatus.NO_SHOW }
        engine.complain(order.id, ComplaintReason.NO_SHOW, "Tôi cần được kiểm tra lại thông tin này.", emptyList(), order.noShowDeadline!! + 1)
        assertFalse(engine.snapshot.orders.first { it.id == order.id }.complaint!!.withinNoShowWindow)
    }
    @Test fun `money rounding is half up and profile inputs do not silently bypass validation`() {
        assertEquals(1L, OrderPolicy(depositPercent = 50).deposit(1))
        val engine = CustomerOrderEngine()
        assertThrows(IllegalArgumentException::class.java) { engine.updateProfile("A", "") }
        assertThrows(IllegalArgumentException::class.java) { engine.updateProfile("Khách thử", "123") }
        engine.updateProfile(" Khách thử ", "0901234567")
        assertEquals("Khách thử", engine.snapshot.profile.name)
    }
    @Test fun `saved orders retain snapped prices policy balances and expiry across restoration`() {
        val engine = CustomerOrderEngine(); paid(engine, PaymentPlan.DEPOSIT)
        val gson = Gson()
        val restored = CustomerOrderEngine(gson.fromJson(gson.toJson(engine.snapshot), CustomerSnapshot::class.java))
        assertEquals(engine.snapshot, restored.snapshot)
        restored.refresh(slot.second)
        assertEquals(OrderStatus.VERIFY_PICKUP, restored.snapshot.orders.first().status)
    }
    @Test fun `complaints reject empty descriptions and more than five attachments`() {
        val engine = CustomerOrderEngine(); val order = paid(engine)
        assertThrows(IllegalArgumentException::class.java) { engine.complain(order.id, ComplaintReason.QUALITY, "ngắn", emptyList(), now) }
        assertThrows(IllegalArgumentException::class.java) { engine.complain(order.id, ComplaintReason.QUALITY,
            "Một mô tả vấn đề đủ dài để được gửi đi.", List(6) { Evidence("content://$it", "Ảnh $it") }, now) }
    }
    @Test fun `location distance is calculated from coordinates and absent permission produces no fabricated distance`() {
        val from = CustomerPoint(10.77, 106.7)
        assertEquals(0, distanceBetween(from, StoreLocation(10.77, 106.7, "Điểm thử")))
        assertTrue(distanceBetween(CustomerPoint(0.0, 0.0), StoreLocation(0.0, 1.0, "Điểm thử")) in 111_000..111_300)
        assertTrue(DiscoveryState().boxes.all { it.distanceMeters == -1 })
        val state = DiscoveryState(point = from, district = "TP. Hồ Chí Minh")
        assertTrue(state.boxes.all { it.distanceMeters >= 0 })
        assertEquals(state.boxes.map { it.distanceMeters }.sorted(), state.boxes.map { it.distanceMeters })
    }
}
