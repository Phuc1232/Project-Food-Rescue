package com.foodrescue.app.feature.complaint.domain

import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.payment.domain.*
import com.foodrescue.app.feature.refund.domain.*
import com.foodrescue.app.feature.complaint.presentation.*
import org.junit.Assert.*
import org.junit.Test

class ComplaintReviewRulesTest {
    private val order = BoxReservation("order", 1, 2, PickupWindow(10_000, 20_000), 1000, 9000, 30_000,
        QrPayment("FR123", 60_000, PaymentStatus.SUCCEEDED, 2000), collectedAtMillis = 11_000)
    private val pending = CustomerComplaint("KN123", ComplaintOrder("order", "FR123", "Tiệm Bánh Mộc", "Hộp bánh", 2, 60_000, 1000),
        ComplaintReason.MISSING_ITEMS, "Số lượng sản phẩm trong hộp không đúng mô tả.", emptyList(), ComplaintStatus.PENDING, 12_000, 12_000)
    private val note = "Đã đối chiếu bằng chứng và xác nhận thông tin khách hàng cung cấp."
    private fun received() = pending.review(0, ComplaintReviewAction.RECEIVED, "", 0, 60_000, order, "admin", 13_000)

    @Test fun receiveThenResolvePreservesSubmissionAndAudit() {
        val next = received().review(1, ComplaintReviewAction.RESOLVED, "  $note  ", 0, 60_000, order, "admin", 14_000)
        assertEquals(ComplaintStatus.RESOLVED, next.status)
        assertEquals(note, next.response)
        assertEquals(2L, next.revision)
        assertEquals(pending.description, next.description)
        assertEquals(listOf(ComplaintReviewAction.RECEIVED, ComplaintReviewAction.RESOLVED), next.history.map { it.action })
        assertEquals(OrderStatus.COLLECTED, next.history.last().orderStatus)
        assertNull(next.refundRequest)
    }
    @Test fun rejectionRequiresReasonAndDoesNotCreateRefund() {
        val result = received().review(1, ComplaintReviewAction.REJECTED, note, 0, 60_000, order, "admin", 14_000)
        assertEquals(ComplaintStatus.REJECTED, result.status)
        assertNull(result.refundRequest)
    }
    @Test fun cannotSkipReceiptOrOverwriteStaleAndClosedDecisions() {
        assertThrows(StaleComplaintReview::class.java) { pending.review(0, ComplaintReviewAction.RESOLVED, note, 0, 60_000, order, "admin", 14_000) }
        assertThrows(StaleComplaintReview::class.java) { received().review(0, ComplaintReviewAction.REJECTED, note, 0, 60_000, order, "admin", 14_000) }
        val closed = received().review(1, ComplaintReviewAction.REJECTED, note, 0, 60_000, order, "admin", 14_000)
        assertThrows(StaleComplaintReview::class.java) { closed.review(2, ComplaintReviewAction.RESOLVED, note, 0, 60_000, order, "admin", 15_000) }
    }
    @Test fun invalidResponsesAmountsAndBackdatedAuditAreRejected() {
        listOf("", "  ", "Quá ngắn", "a".repeat(2001), note + '\u0000').forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { received().review(1, ComplaintReviewAction.RESOLVED, invalid, 0, 60_000, order, "admin", 14_000) }
        }
        listOf(-1L, 0L, 60_001L, Long.MAX_VALUE).forEach { amount ->
            assertThrows(IllegalArgumentException::class.java) { received().review(1, ComplaintReviewAction.REFUND_REQUESTED, note, amount, 60_000, order, "admin", 14_000) }
        }
        assertThrows(IllegalArgumentException::class.java) { received().review(1, ComplaintReviewAction.RESOLVED, note, 0, 60_000, order, "admin", 1) }
    }
    @Test fun acceptedPartialRefundIsQueuedAndPaymentIsUnchanged() {
        val result = received().review(1, ComplaintReviewAction.REFUND_REQUESTED, note, 20_000, 60_000, order, "admin", 14_000)
        assertEquals(20_000L, result.refundRequest!!.amount)
        assertEquals("HT-KN123", result.refundRequest.id)
        assertEquals(40_000L, complaintRefundableAmount(order, listOf(result)))
        assertEquals(PaymentStatus.SUCCEEDED, order.payment!!.status)
        assertNull(order.refund)
    }
    @Test fun existingRefundsAndQueuedCasesReserveValueWithoutOverflow() {
        val cancelled = order.copy(collectedAtMillis = null, cancelledAtMillis = 11_000,
            refund = OrderRefund(50_000, RefundStatus.FAILED, 11_000, 11_000))
        assertEquals(10_000L, complaintRefundableAmount(cancelled, emptyList()))
        val queued = received().review(1, ComplaintReviewAction.REFUND_REQUESTED, note, 20_000, 60_000, order, "admin", 14_000)
        assertEquals(0L, complaintRefundableAmount(cancelled, listOf(queued)))
        assertEquals(0L, complaintRefundableAmount(order, List(10) { queued.copy(id = "KN$it") }))
    }
    @Test fun missingUnpaidAndActivePickupCannotRequestRefund() {
        assertEquals(0L, complaintRefundableAmount(null, emptyList()))
        assertEquals(0L, complaintRefundableAmount(order.copy(collectedAtMillis = null), emptyList()))
        assertEquals(0L, complaintRefundableAmount(order.copy(collectedAtMillis = null, payment = null), emptyList()))
        assertThrows(IllegalArgumentException::class.java) { received().review(1, ComplaintReviewAction.REFUND_REQUESTED, note, 1, 0, null, "admin", 14_000) }
    }
    @Test fun queueSearchIgnoresAccentsAndKeepsOldestFirst() {
        val older = ComplaintReviewCase("a", pending)
        val newer = ComplaintReviewCase("b", pending.copy(id = "KN456", createdAtMillis = 12_100, updatedAtMillis = 12_100))
        val state = AdminComplaintsState(cases = listOf(newer, older), query = "  tiem banh moc  ")
        assertEquals(listOf(older, newer), state.visible)
        assertEquals(2, state.count(AdminComplaintFilter.PENDING))
        assertEquals(0, state.copy(filter = AdminComplaintFilter.CLOSED).visible.size)
    }
    @Test fun callerCannotInflateAvailableRefundBeyondOrderRules() {
        val existingRefund = order.copy(collectedAtMillis = null, cancelledAtMillis = 11_000,
            refund = OrderRefund(50_000, RefundStatus.FAILED, 11_000, 11_000))
        assertThrows(IllegalArgumentException::class.java) {
            received().review(1, ComplaintReviewAction.REFUND_REQUESTED, note, 20_000, Long.MAX_VALUE, existingRefund, "admin", 14_000)
        }
        assertThrows(IllegalArgumentException::class.java) {
            received().review(1, ComplaintReviewAction.REFUND_REQUESTED, note, 1, 60_000, order.copy(collectedAtMillis = null), "admin", 14_000)
        }
    }
}
