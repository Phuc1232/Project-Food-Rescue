package com.foodrescue.app.feature.refund.domain

import com.foodrescue.app.feature.finance.domain.*
import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.payment.domain.*
import org.junit.Assert.*
import org.junit.Test

class RefundRevenueTest {
    private val order = BoxReservation("one", 1, 2, PickupWindow(10_000, 20_000), 1000, 9000, 30_000,
        QrPayment("FRONE", 60_000, PaymentStatus.SUCCEEDED, 2000), collectedAtMillis = 11_000)
    private fun refund(amount: Long = 20_000, status: AdminRefundStatus = AdminRefundStatus.COMPLETED, id: String = "HT1", at: Long = 15_000) =
        ManagedRefund(id, RefundSource.COMPLAINT, "KN1", "Thiếu món", amount, 12_000, status,
            listOf(RefundEvent(status, "Hệ thống", at, "Kết quả xử lý khoản hoàn")))
    private fun snapshot(value: BoxReservation, records: List<RevenueSettlement> = emptyList(), queued: Map<String, List<RefundMovement>> = emptyMap()) =
        buildRevenueSnapshot(7, listOf(value), emptySet(), records, null, 2000, 30_000, queued)

    @Test fun partialRefundReducesEarningsAndCommissionExactlyOnce() {
        val value = snapshot(order.copy(adminRefunds = listOf(refund())))
        val entry = value.entries.single()
        assertEquals(20_000L, entry.refunded); assertEquals(8_000L, entry.commission); assertEquals(32_000L, entry.net)
        assertEquals(32_000L, value.awaitingPayout); assertEquals(20_000L, value.settlements.single().lines.single().refunded)
    }
    @Test fun fullRefundReleasesNoPayout() {
        val value = snapshot(order.copy(adminRefunds = listOf(refund(60_000))))
        assertEquals(RevenueEntryStatus.REFUNDED, value.entries.single().status)
        assertEquals(0L, value.awaitingPayout); assertEquals(0L, value.entries.single().net)
    }
    @Test fun pendingAndFailedRefundKeepCollectedOrderHeld() {
        listOf(AdminRefundStatus.PROCESSING, AdminRefundStatus.FAILED).forEach { status ->
            val value = snapshot(order.copy(adminRefunds = listOf(refund(status = status))))
            assertEquals(RevenueEntryStatus.REFUND_PENDING, value.entries.single().status)
            assertEquals(20_000L, value.entries.single().refundPending); assertEquals(0L, value.awaitingPayout)
        }
    }
    @Test fun acceptedQueuedRefundIsIncludedBeforeAdminConfirmation() {
        val value = snapshot(order, queued = mapOf("one" to listOf(RefundMovement(20_000, 12_000))))
        assertEquals(20_000L, value.entries.single().refundPending)
        assertEquals(0L, value.entries.single().net)
    }
    @Test fun finalizedSettlementStaysImmutableAndCreatesExplicitAdjustment() {
        val record = RevenueSettlement("SET1", 7, RevenueWindow(10_000, 20_000),
            listOf(SettlementLine("one", "FRONE", "Hộp bánh", 2, 60_000, 2000, 12_000)), SettlementStatus.PAID,
            SettlementDestination("Vietcombank", "NGUYEN AN", "1234"), 13_000, 13_000, "TX1")
        val value = snapshot(order.copy(adminRefunds = listOf(refund())), listOf(record))
        assertEquals(record, value.settlements.single()); assertEquals(48_000L, value.paidOut)
        assertEquals(16_000L, value.settlementAdjustment); assertEquals(0L, value.awaitingPayout)
    }
    @Test fun eachRefundIsReportedInItsOwnCompletionPeriod() {
        val value = snapshot(order.copy(adminRefunds = listOf(refund(), refund(10_000, id = "HT2", at = 25_000))))
        assertEquals(20_000L, revenueReport(value, RevenueWindow(14_000, 20_000)).refunded)
        assertEquals(10_000L, revenueReport(value, RevenueWindow(20_000, 30_000)).refunded)
        assertEquals(30_000L, revenueReport(value, RevenueWindow(0, 30_000)).earnedGross)
    }
}
