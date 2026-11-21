package com.foodrescue.app.feature.finance.domain

import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.payment.domain.*
import com.foodrescue.app.feature.refund.domain.*
import com.foodrescue.app.feature.store.domain.StorePayoutAccount
import com.foodrescue.app.feature.finance.presentation.PolicyDraft
import org.junit.Assert.*
import org.junit.Test

class AdminFinanceRulesTest {
    private val now = 1_800_000_000_000L
    private val reason = "Đã kiểm tra các giao dịch trong kỳ."
    private val bank = StorePayoutAccount("Vietcombank", "NGUYEN AN", "0123456789")
    private fun order(id: String = "one") = BoxReservation(id, 1, 1, PickupWindow(now - 1000, now + 1000), now - 4000,
        now - 2000, 100000, QrPayment("FR$id", 100000, PaymentStatus.SUCCEEDED, now - 3000), collectedAtMillis = now - 500)
    private fun report(orders: List<BoxReservation>, records: List<RevenueSettlement> = emptyList()) =
        buildRevenueSnapshot(7, orders, emptySet(), records, bank, 2000, now)
    private fun pending() = ManagedSettlement("Tiệm An", report(listOf(order())).settlements.single(), bank)
    private fun change(value: ManagedSettlement, action: SettlementAction, reference: String = "") =
        value.copy(record = value.decide(SettlementDecision(action, reason, reference), "admin", now, "DS-ONE"))
    private fun paid() = change(change(change(pending(), SettlementAction.CONFIRM), SettlementAction.START), SettlementAction.COMPLETE, "VNP-123456").record
    private fun refunded(order: BoxReservation, amount: Long) = order.copy(adminRefunds = listOf(ManagedRefund("refund", RefundSource.COMPLAINT,
        "complaint", reason, amount, now - 100, AdminRefundStatus.COMPLETED,
        listOf(RefundEvent(AdminRefundStatus.COMPLETED, "admin", now, reason)))))

    @Test fun scheduledPolicyHasAnExactBoundaryAndDoesNotRewriteHistory() {
        val book = FinancialPolicyBook().publish(0, FinancialTerms(commissionBps = 1500), now + 1000, "admin", reason, now)
        assertEquals(2000, book.termsAt(now + 999).commissionBps)
        assertEquals(1500, book.termsAt(now + 1000).commissionBps)
        val old = order().copy(financialTerms = book.termsAt(now))
        val new = order("two").copy(financialTerms = book.termsAt(now + 1000))
        assertEquals(listOf(20000L, 15000L), report(listOf(old, new)).entries.map { it.commission })
    }
    @Test fun staleConcurrentAndRetroactivePolicyPublicationsAreRejected() {
        val book = FinancialPolicyBook().publish(0, FinancialTerms(commissionBps = 1500), now + 1000, "admin", reason, now)
        assertTrue(runCatching { book.publish(0, FinancialTerms(commissionBps = 1000), now + 2000, "admin", reason, now) }.exceptionOrNull() is StaleFinanceData)
        assertTrue(runCatching { book.publish(1, FinancialTerms(commissionBps = 1000), now + 2000, "admin", reason, now) }.isFailure)
        assertTrue(runCatching { FinancialPolicyBook().publish(0, FinancialTerms(commissionBps = 1000), now - 1, "admin", reason, now) }.isFailure)
    }
    @Test fun cancellationUsesTheOrdersOwnDeadlineAndRefundRate() {
        val value = order().copy(collectedAtMillis = null, pickup = PickupWindow(now + 3600000, now + 7200000),
            financialTerms = FinancialTerms(cancellationMinutes = 30, cancellationRefundBps = 7500))
        assertEquals(75000L, cancellationQuote(value, now + 1799999).amount)
        assertEquals(CancellationIssue.PICKUP_STARTED, cancellationQuote(value, now + 1800000).issue)
        assertEquals(0L, cancellationQuote(value.copy(financialTerms = FinancialTerms(cancellationRefundBps = 0)), now).amount)
    }
    @Test fun noShowRespectsAdditionalWaitingTime() {
        val value = order().copy(collectedAtMillis = null, financialTerms = FinancialTerms(noShowGraceMinutes = 15))
        val draft = NoShowDraft("request", value.id, customerAbsent = true, storeReady = true)
        assertTrue(NoShowIssue.EARLY in draft.noShowIssues(null, value, emptyList(), value.noShowEligibleAt - 1))
        assertFalse(NoShowIssue.EARLY in draft.noShowIssues(null, value, emptyList(), value.noShowEligibleAt))
    }
    @Test fun confirmationLocksTheBankAndRecordsEveryPaymentStage() {
        val confirmed = change(pending(), SettlementAction.CONFIRM)
        assertEquals("6789", confirmed.record.destination!!.lastFour)
        assertEquals("0123456789", confirmed.record.destination!!.accountNumber)
        val paid = change(change(confirmed, SettlementAction.START), SettlementAction.COMPLETE, "BANK-12345")
        assertEquals(listOf(SettlementStatus.READY, SettlementStatus.PROCESSING, SettlementStatus.PAID), paid.record.history.map { it.status })
        assertTrue(paid.actions.isEmpty())
        assertEquals(80000L, report(listOf(order()), listOf(paid.record)).paidOut)
    }
    @Test fun invalidTransferReferenceAndSkippingProcessingAreRejected() {
        val ready = change(pending(), SettlementAction.CONFIRM)
        assertTrue(runCatching { change(ready, SettlementAction.COMPLETE, "BANK-12345") }.isFailure)
        val processing = change(ready, SettlementAction.START)
        assertTrue(runCatching { change(processing, SettlementAction.COMPLETE, "123") }.isFailure)
    }
    @Test fun failedPayoutRetriesTheSameRecordAndNeverCreatesAnotherPeriod() {
        val failed = change(change(change(pending(), SettlementAction.CONFIRM), SettlementAction.START), SettlementAction.FAIL)
        assertEquals("DS-ONE", failed.record.id)
        val paid = change(change(failed, SettlementAction.START), SettlementAction.COMPLETE, "BANK-12345")
        val snapshot = report(listOf(order()), listOf(paid.record))
        assertEquals(1, snapshot.settlements.size); assertEquals(80000L, snapshot.paidOut)
    }
    @Test fun voidedUnpaidPeriodReleasesOrdersButRetainsTheAudit() {
        val cancelled = change(change(pending(), SettlementAction.CONFIRM), SettlementAction.VOID)
        val snapshot = report(listOf(order()), listOf(cancelled.record))
        assertEquals(2, snapshot.settlements.size)
        assertEquals(80000L, snapshot.awaitingPayout)
        assertEquals(2, cancelled.record.history.size)
    }
    @Test fun refundAfterPayoutIsDeductedOnceFromTheNextSettlement() {
        val first = paid()
        val orders = listOf(refunded(order(), 25000), order("two"))
        val report = report(orders, listOf(first))
        val pending = report.settlements.single { it.status == SettlementStatus.PENDING }
        assertEquals(20000L, pending.adjustment); assertEquals(60000L, pending.net)
        val confirmed = ManagedSettlement("Tiệm An", pending, bank).decide(SettlementDecision(SettlementAction.CONFIRM, reason), "admin", now, "DS-TWO")
        val again = report(orders, listOf(first, confirmed))
        assertEquals(60000L, again.awaitingPayout)
        assertEquals(2, again.settlements.size)
    }
    @Test fun untrustedOffsetsCannotDeductAnUnrelatedOrder() {
        val paid = paid()
        val next = change(pending(), SettlementAction.CONFIRM).record.copy(id = "DS-TWO",
            lines = listOf(SettlementLine("two", "FRtwo", "Box", 1, 100000, 2000, 20000)),
            adjustments = listOf(SettlementAdjustment("DS-ONE", "missing", 5000)))
        assertTrue(runCatching { report(listOf(order(), order("two")), listOf(paid, next)) }.isFailure)
    }
    @Test fun zeroNetReconciliationClosesWithoutATransfer() {
        val pending = pending().let { it.copy(record = it.record.copy(lines = it.record.lines.map { line -> line.copy(commissionBps = 10000, commission = line.gross) })) }
        val closed = change(pending, SettlementAction.CONFIRM)
        assertEquals(SettlementStatus.PAID, closed.record.status)
        assertEquals(0L, closed.record.net); assertTrue(closed.record.transferReference!!.startsWith("BT-"))
    }
    @Test fun policyFormRejectsFractionsOverflowAndInvalidDates() {
        assertEquals(1525, PolicyDraft(commission = "15,25").terms()!!.commissionBps)
        for (value in listOf("NaN", "1e2", "-1", "100.01", "20.001", "")) assertNull(PolicyDraft(commission = value).terms())
        assertNull(PolicyDraft(noShowGrace = "121").terms())
        assertNull(PolicyDraft(scheduled = true, effectiveText = "31/02/2030 10:00").effectiveAt(now))
        assertNotNull(PolicyDraft(scheduled = true, effectiveText = "01/12/2030 10:00").effectiveAt(now))
    }

    @Test fun insufficientRevenueCarriesOffsetsUntilAnotherOrderArrives() {
        val original = paid()
        val small = order("two").copy(unitPrice = 10000,
            payment = QrPayment("FRtwo", 10000, PaymentStatus.SUCCEEDED, now - 3000))
        val afterRefund = refunded(order(), 50000)
        val blocked = report(listOf(afterRefund, small), listOf(original)).settlements.single { it.status == SettlementStatus.PENDING }
        assertEquals(-32000L, blocked.net)
        val recovered = report(listOf(afterRefund, small, order("three")), listOf(original)).settlements.single { it.status == SettlementStatus.PENDING }
        assertEquals(40000L, recovered.adjustment)
        assertEquals(48000L, recovered.net)
    }

    @Test fun laterRefundOnlyAddsTheUnassignedOffset() {
        val first = paid()
        val afterFirstRefund = listOf(refunded(order(), 25000), order("two"))
        val pending = report(afterFirstRefund, listOf(first)).settlements.single { it.status == SettlementStatus.PENDING }
        val second = ManagedSettlement("Tiệm An", pending, bank).decide(
            SettlementDecision(SettlementAction.CONFIRM, reason), "admin", now, "DS-TWO")
        val next = report(listOf(refunded(order(), 50000), order("two"), order("three")), listOf(first, second))
            .settlements.single { it.status == SettlementStatus.PENDING }
        assertEquals(20000L, next.adjustment)
        assertEquals(60000L, next.net)
    }
}
