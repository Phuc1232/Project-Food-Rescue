package com.foodrescue.app.feature.refund.domain

import com.foodrescue.app.feature.complaint.domain.*
import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.payment.domain.*
import org.junit.Assert.*
import org.junit.Test

class AdminRefundRulesTest {
    private val order = BoxReservation("one", 1, 2, PickupWindow(10_000, 20_000), 1000, 9000, 30_000,
        QrPayment("FRONE", 60_000, PaymentStatus.SUCCEEDED, 2000), collectedAtMillis = 11_000)
    private val note = "Đã đối chiếu giao dịch và chấp thuận khoản hoàn."
    private fun row(value: BoxReservation = order, amount: Long = 20_000, reserved: Long = 0) =
        RefundCase("account", value.id, "HT-ONE", RefundSource.COMPLAINT, "KN1", note, amount, 12_000, value,
            value.adminRefunds.singleOrNull { it.id == "HT-ONE" }, reserved)
    private fun owner() = order.copy(collectedAtMillis = null,
        ownerCancellation = OwnerCancellationRequest(OwnerCancellationReason.OUT_OF_STOCK, "Không còn đủ hộp", 12_000))
    private fun ownerRow(value: BoxReservation = owner()) = row(value, 60_000).copy(source = RefundSource.STORE_CANCELLATION)

    @Test fun partialComplaintRefundPreservesCollectionAndOriginalPayment() {
        val updated = row().decide(true, note, "admin", 13_000)
        assertEquals(order.payment, updated.payment); assertEquals(order.collectedAtMillis, updated.collectedAtMillis)
        assertNull(updated.cancelledAtMillis); assertNull(updated.refund)
        assertEquals(20_000L, updated.pendingRefundAmount); assertEquals(0L, updated.completedRefundAmount)
    }
    @Test fun ownerApprovalCancelsAndCreatesRefundInOneStateTransition() {
        val updated = ownerRow().decide(true, note, "admin", 13_000)
        assertEquals(13_000L, updated.cancelledAtMillis); assertNull(updated.ownerCancellation)
        assertFalse(updated.occupiesStock(13_000)); assertNull(updated.pickupCode(13_000))
        assertEquals(60_000L, updated.pendingRefundAmount)
    }
    @Test fun rejectionPreservesPaidOrderAndRecordsReasonWithoutRefund() {
        val updated = ownerRow().decide(false, note, "admin", 13_000)
        assertNull(updated.cancelledAtMillis); assertNull(updated.ownerCancellation)
        assertTrue(updated.occupiesStock(13_000)); assertNotNull(updated.pickupCode(13_000))
        assertEquals(0L, updated.pendingRefundAmount)
        assertEquals(note, updated.adminRefunds.single().history.single().note)
    }
    @Test fun cannotRejectAnAlreadyAcceptedComplaintOrRetryProcessing() {
        assertThrows(IllegalArgumentException::class.java) { row().decide(false, note, "admin", 13_000) }
        val updated = row().decide(true, note, "admin", 13_000)
        assertThrows(IllegalArgumentException::class.java) { row(updated).decide(true, note, "admin", 14_000) }
    }
    @Test fun failedRetryKeepsIdentityAmountAndAudit() {
        val initial = row().decide(true, note, "admin", 13_000)
        val refund = initial.adminRefunds.single().let { it.copy(status = AdminRefundStatus.FAILED,
            history = it.history + RefundEvent(AdminRefundStatus.FAILED, "Hệ thống", 14_000, "Mất kết nối dịch vụ")) }
        val retried = row(initial.copy(adminRefunds = listOf(refund))).decide(true, note, "admin", 15_000)
        assertEquals(refund.id, retried.adminRefunds.single().id)
        assertEquals(refund.amount, retried.pendingRefundAmount)
        assertEquals(3, retried.adminRefunds.single().history.size)
    }
    @Test fun invalidAmountsNotesAndBackdatingAreBlocked() {
        listOf(0L, -1, 60_001).forEach { amount -> assertThrows(IllegalArgumentException::class.java) {
            row(amount = amount).decide(true, note, "admin", 13_000)
        } }
        listOf("", "ngắn", "x".repeat(501), note + '\u0000').forEach { text -> assertFalse(validRefundNote(text)) }
        assertThrows(IllegalArgumentException::class.java) { row().decide(true, note, "admin", 1000) }
    }
    @Test fun otherQueuedAndFailedRefundsReduceAvailableAmount() {
        assertEquals(15_000L, row(reserved = 45_000).availableAmount)
        assertFalse(row(reserved = 45_000).canProcess)
        assertThrows(IllegalArgumentException::class.java) { row(reserved = 45_000).decide(true, note, "admin", 13_000) }
    }
    @Test fun missingOrUnpaidOrderCannotBeProcessed() {
        assertFalse(row().copy(order = null).canProcess)
        val unpaid = order.copy(collectedAtMillis = null, payment = null)
        assertFalse(row(unpaid).canProcess)
    }
    @Test fun cancelledRefundAndManagedRefundCannotExceedPayment() {
        val managed = row().decide(true, note, "admin", 13_000).adminRefunds
        assertThrows(IllegalArgumentException::class.java) {
            order.copy(collectedAtMillis = null, cancelledAtMillis = 13_000,
                refund = OrderRefund(50_000, RefundStatus.FAILED, 13_000, 13_000), adminRefunds = managed)
        }
    }
    @Test fun complaintReservationIsNotCountedTwiceAfterProcessingStarts() {
        val updated = row().decide(true, note, "admin", 13_000)
        val complaint = CustomerComplaint("KN1", ComplaintOrder("one", "FRONE", "Tiệm bánh", "Hộp bánh", 2, 60_000, 1000),
            ComplaintReason.MISSING_ITEMS, "Thiếu sản phẩm so với nội dung đã xác nhận.", emptyList(), ComplaintStatus.RESOLVED,
            12_000, 12_000, note, refundRequest = ComplaintRefundRequest("HT-ONE", 20_000, 12_000))
        assertEquals(40_000L, complaintRefundableAmount(updated, listOf(complaint)))
    }
}
