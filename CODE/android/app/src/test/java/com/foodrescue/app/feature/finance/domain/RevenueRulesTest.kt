package com.foodrescue.app.feature.finance.domain

import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.payment.domain.*
import com.foodrescue.app.feature.refund.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class RevenueRulesTest {
    private fun time(value: String): Long = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
    }.parse(value)!!.time
    private val now = time("2026-11-16 12:00")
    private fun order(id: String = "one", price: Int = 49000, collected: Boolean = true) = BoxReservation(
        id, 1, 2, PickupWindow(now - 3600000, now + 3600000), now - 7200000, now - 6600000, price,
        QrPayment("FR$id", price * 2L, PaymentStatus.SUCCEEDED, now - 7000000),
        collectedAtMillis = if (collected) now - 1800000 else null)
    private fun snapshot(orders: List<BoxReservation>, holds: Set<String> = emptySet(), records: List<RevenueSettlement> = emptyList()) =
        buildRevenueSnapshot(7, orders, holds, records, null, 2000, now)

    @Test fun twentyPercentCommissionKeepsWholeDongRoundingWithoutOverflow() {
        assertEquals(19600L, revenueCommission(98000, 2000))
        assertEquals(1L, revenueCommission(3, 2000))
        assertEquals(0L, revenueCommission(2, 2000))
        assertEquals(922337203685477581L, revenueCommission(4611686018427387904L, 2000))
    }
    @Test fun completedPaidOrderProducesEarningsAndOnlyPendingSettlement() {
        val value = snapshot(listOf(order()))
        val report = revenueReport(value, RevenuePeriod.DAY.window(now))
        assertEquals(98000L, report.received); assertEquals(98000L, report.earnedGross)
        assertEquals(19600L, report.commission); assertEquals(78400L, report.net)
        assertEquals(78400L, value.awaitingPayout); assertEquals(0L, value.paidOut)
        assertEquals(SettlementStatus.PENDING, value.settlements.single().status)
        assertEquals(value.settlements.single().id, value.entries.single().settlementId)
    }
    @Test fun failedProcessingExpiredAndFuturePaymentsNeverBecomeReceipts() {
        val sample = order()
        val unpaid = listOf(PaymentStatus.FAILED, PaymentStatus.PROCESSING, PaymentStatus.EXPIRED, PaymentStatus.READY)
            .mapIndexed { index, status -> sample.copy(id = "$index", collectedAtMillis = null, payment = sample.payment!!.copy(status = status)) }
        assertTrue(snapshot(unpaid + sample.copy(payment = sample.payment!!.copy(updatedAtMillis = now + 1))).entries.isEmpty())
    }
    @Test fun awaitingOverdueAndCancellationRequestedOrdersAreNotEarnings() {
        val waiting = order(collected = false)
        val overdue = waiting.copy(id = "overdue", pickup = PickupWindow(now - 3600000, now - 1))
        val cancel = waiting.copy(id = "cancel", ownerCancellation = OwnerCancellationRequest(OwnerCancellationReason.OUT_OF_STOCK, "", now))
        val value = snapshot(listOf(waiting, overdue, cancel))
        assertEquals(3, value.entries.size)
        assertEquals(0L, revenueReport(value, RevenuePeriod.DAY.window(now)).net)
        assertTrue(value.settlements.isEmpty())
        assertEquals(RevenueEntryStatus.HELD, value.entries.single { it.order.id == "cancel" }.status)
    }
    @Test fun openComplaintHoldsCollectedOrderUntilResolved() {
        val held = snapshot(listOf(order()), setOf("one"))
        assertEquals(RevenueEntryStatus.HELD, held.entries.single().status)
        assertTrue(held.settlements.isEmpty())
        assertEquals(78400L, snapshot(listOf(order())).awaitingPayout)
    }
    @Test fun pendingAndFailedRefundsRemainReservedAndCompletedRefundDoesNotEarn() {
        val cancelled = order(collected = false).copy(cancelledAtMillis = now)
        for (status in RefundStatus.entries) {
            val value = snapshot(listOf(cancelled.copy(refund = OrderRefund(98000, status, now, now))))
            val line = value.entries.single()
            assertEquals(if (status == RefundStatus.COMPLETED) 98000L else 0L, line.refunded)
            assertEquals(if (status == RefundStatus.COMPLETED) 0L else 98000L, line.refundPending)
            assertEquals(0L, line.net); assertTrue(value.settlements.isEmpty())
        }
    }
    @Test fun calendarBoundariesUseVietnamMondayAndDoNotIncludeNextPeriod() {
        val start = time("2026-11-16 00:00")
        val window = RevenuePeriod.WEEK.window(now)
        assertEquals(start, window.start); assertEquals(time("2026-11-23 00:00"), window.endExclusive)
        assertFalse(time("2026-11-23 00:00") in window)
        assertEquals(time("2026-10-01 00:00"), RevenuePeriod.MONTH.window(now, -1).start)
        assertEquals(time("2026-11-15 00:00"), RevenuePeriod.DAY.window(now, -1).start)
    }
    @Test fun paymentDayAndCollectionDayAreReportedSeparately() {
        val sample = order().copy(payment = order().payment!!.copy(updatedAtMillis = time("2026-11-15 23:59")))
        val value = snapshot(listOf(sample))
        val today = revenueReport(value, RevenuePeriod.DAY.window(now))
        assertEquals(0L, today.received); assertEquals(78400L, today.net)
        val yesterday = revenueReport(value, RevenuePeriod.DAY.window(now, -1))
        assertEquals(98000L, yesterday.received); assertEquals(0L, yesterday.net)
    }
    private fun record(id: String = "settlement", status: SettlementStatus = SettlementStatus.PAID) = RevenueSettlement(
        id, 7, RevenueWindow(time("2026-11-16 00:00"), time("2026-11-23 00:00")),
        listOf(SettlementLine("one", "FRone", "Hộp bánh", 2, 98000, 1500, 14700)), status,
        SettlementDestination("Vietcombank", "NGUYEN AN", "6789"), now, if (status == SettlementStatus.PAID) now else null,
        if (status == SettlementStatus.PAID) "TRANSFER-1" else null)
    @Test fun confirmedSnapshotRetainsItsFeeAndDoesNotCreateSecondPendingPayout() {
        val value = snapshot(listOf(order()), records = listOf(record()))
        assertEquals(83300L, value.paidOut); assertEquals(0L, value.awaitingPayout)
        assertEquals(1, value.settlements.size)
        assertEquals(14700L, value.entries.single().commission)
    }
    @Test fun passingTimeNeverChangesProcessingRecordToPaid() {
        val value = snapshot(listOf(order()), records = listOf(record(status = SettlementStatus.PROCESSING)))
        assertEquals(0L, value.paidOut); assertEquals(83300L, value.awaitingPayout)
        assertEquals(SettlementStatus.PROCESSING, value.settlements.single().status)
    }
    @Test fun duplicatedSettlementAssignmentFailsInsteadOfDoubleCounting() {
        assertThrows(IllegalArgumentException::class.java) { snapshot(listOf(order()), records = listOf(record("a"), record("b"))) }
    }
    @Test fun paidStateNeedsTransferEvidenceAndMatchingStore() {
        assertThrows(IllegalArgumentException::class.java) { snapshot(listOf(order()), records = listOf(record().copy(transferReference = null))) }
        assertThrows(IllegalArgumentException::class.java) { snapshot(listOf(order()), records = listOf(record().copy(storeId = 9))) }
    }
}
