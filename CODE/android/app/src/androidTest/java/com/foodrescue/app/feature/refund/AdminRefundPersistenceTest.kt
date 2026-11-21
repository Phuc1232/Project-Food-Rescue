package com.foodrescue.app.feature.refund

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.foodrescue.app.core.datastore.LocalCommerceLock
import com.foodrescue.app.feature.complaint.data.*
import com.foodrescue.app.feature.complaint.domain.*
import com.foodrescue.app.feature.discovery.data.LocalCatalogRepository
import com.foodrescue.app.feature.order.data.*
import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.payment.domain.*
import com.foodrescue.app.feature.refund.data.*
import com.foodrescue.app.feature.refund.domain.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class AdminRefundPersistenceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val ledger get() = LocalOrderLedger(context)
    private val accounts = mutableListOf<String>()
    private val note = "Đã đối chiếu và xác nhận hoàn tiền theo yêu cầu."
    private var now = 20_000L
    private var outcome = RefundResult.COMPLETED
    private val keys = mutableListOf<String>()
    private val processor = RefundProcessor { key, _, _ -> keys += key; outcome }
    private fun admin() = LocalAdminRefundRepository(context, "admin@foodrescue.vn", processor, { now })
    private data class Fixture(val email: String, val account: String, val order: BoxReservation, val complaint: CustomerComplaint?)
    private fun fixture(owner: Boolean = false, id: String = UUID.randomUUID().toString()): Fixture {
        val email = "refund-${UUID.randomUUID()}@example.com"
        val account = LocalOrderLedger.accountKey(email); accounts += account
        val order = BoxReservation(id, 1, 2, PickupWindow(10_000, 30_000), 1000, 9000, 30_000,
            QrPayment("FR$id", 60_000, PaymentStatus.SUCCEEDED, 2000), collectedAtMillis = if (owner) null else 11_000,
            ownerCancellation = if (owner) OwnerCancellationRequest(OwnerCancellationReason.OUT_OF_STOCK, "Không đủ hộp", 12_000) else null)
        val complaint = if (owner) null else CustomerComplaint("KN$id", ComplaintOrder(id, order.reference, "Tiệm bánh Mộc", "Hộp bánh", 2, 60_000, 1000),
            ComplaintReason.MISSING_ITEMS, "Hộp bị thiếu sản phẩm theo nội dung đã xác nhận.", emptyList(), ComplaintStatus.RESOLVED,
            12_000, 13_000, note, refundRequest = ComplaintRefundRequest("HT-$id", 20_000, 13_000))
        synchronized(LocalCommerceLock) {
            ledger.write(ledger.read() + LocalOrderLedger.Row(account, order))
            if (complaint != null) LocalComplaintRecords(context).write(account, listOf(complaint))
        }
        return Fixture(email, account, order, complaint)
    }
    private suspend fun row(f: Fixture) = admin().load().single { it.accountKey == f.account && it.orderId == f.order.id }
    private fun saved(f: Fixture) = ledger.read().single { it.account == f.account && it.order.id == f.order.id }.order
    private fun customer(f: Fixture) = LocalComplaintRepository(context, f.email, LocalEvidenceStore(context, f.email))
    @After fun cleanup() {
        synchronized(LocalCommerceLock) {
            ledger.write(ledger.read().filterNot { it.account in accounts })
            accounts.forEach {
                listOf("customer_complaints_$it", "customer_orders_$it").forEach { name ->
                    // Wait for earlier apply() writes before removing this test's preferences.
                    check(context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit())
                    context.deleteSharedPreferences(name)
                }
                context.getSharedPreferences("commerce_orders.v2", Context.MODE_PRIVATE).edit().remove("migrated.$it").commit()
            }
        }
    }
    @Test fun failedRetryCompletesWithSameKeyAndCustomerProjection() = runBlocking {
        val f = fixture(); val pending = row(f)
        admin().decide(pending, true, note); outcome = RefundResult.UNAVAILABLE; now += 3000
        val failed = row(f); assertEquals(AdminRefundStatus.FAILED, failed.status)
        admin().decide(failed, true, note); outcome = RefundResult.COMPLETED; now += 3000
        val completed = row(f)
        assertEquals(AdminRefundStatus.COMPLETED, completed.status)
        assertEquals(1, keys.distinct().size); assertEquals(2, keys.size)
        assertEquals(4, completed.transaction!!.history.size)
        assertEquals(completed.transaction, customer(f).load().single().refundTransaction)
        assertEquals(f.order.collectedAtMillis, saved(f).collectedAtMillis)
        assertNull(saved(f).refund)
        assertTrue(LocalComplaintHolds(context).read(f.account, setOf(f.order.id), setOf(completed.id)).isEmpty())
    }
    @Test fun parallelConfirmationsProduceOnlyOneDurableInstruction() = runBlocking {
        val f = fixture(); val before = row(f)
        val results = List(2) { async(Dispatchers.IO) { runCatching { admin().decide(before, true, note) } } }.awaitAll()
        assertEquals(1, results.count { it.isSuccess }); assertTrue(results.single { it.isFailure }.exceptionOrNull() is StaleRefund)
        assertEquals(1, saved(f).adminRefunds.size)
    }
    @Test fun staleOrderRequiresAnotherReview() = runBlocking {
        val f = fixture(); val before = row(f)
        synchronized(LocalCommerceLock) { ledger.write(ledger.read().map { if (it.account == f.account) it.copy(order = it.order.copy(
            payment = it.order.payment!!.copy(updatedAtMillis = 3000))) else it }) }
        try { admin().decide(before, true, note); fail("Stale order was processed") } catch (_: StaleRefund) { }
        assertTrue(saved(f).adminRefunds.isEmpty())
    }
    @Test fun failedWriteLeavesOrderAndAuditUnchangedAndCanRetry() = runBlocking {
        val f = fixture(owner = true); val before = row(f)
        val failing = LocalAdminRefundRepository(context, "admin@foodrescue.vn", processor, { now }, ledger, { error("Write failed") })
        try { failing.decide(before, true, note); fail("Write should fail") } catch (_: IllegalStateException) { }
        assertEquals(f.order, saved(f))
        admin().decide(before, true, note)
        assertNotNull(saved(f).cancelledAtMillis); assertNull(saved(f).ownerCancellation)
        assertEquals(60_000L, saved(f).pendingRefundAmount)
    }
    @Test fun oldCustomerSaveCannotUndoAdminCancellationOrRetryIt() = runBlocking {
        val f = fixture(owner = true); val before = row(f)
        admin().decide(before, true, note)
        val shared = SharedOrderHistoryStore(f.email, ledger, LocalOrderHistoryStore(context, f.email), LocalCatalogRepository()) { now }
        shared.save(listOf(f.order)); now += 3000; row(f)
        val loaded = shared.load().single()
        assertNotNull(loaded.cancelledAtMillis); assertNull(loaded.refund)
        assertEquals(AdminRefundStatus.COMPLETED, loaded.adminRefunds.single().status)
        shared.save(listOf(f.order)); assertEquals(loaded, shared.load().single())
    }
    @Test fun rejectClearsOwnerRequestButRetainsDecisionAndOriginalPayment() = runBlocking {
        val f = fixture(owner = true)
        val rejected = admin().decide(row(f), false, note)
        assertEquals(AdminRefundStatus.REJECTED, rejected.status)
        assertNull(saved(f).ownerCancellation); assertNull(saved(f).cancelledAtMillis)
        assertEquals(f.order.payment, saved(f).payment)
        assertEquals(rejected.transaction, row(f).transaction)
    }
    @Test fun restartDoesNotProcessCompletedRequestAgain() = runBlocking {
        val f = fixture(); admin().decide(row(f), true, note); now += 3000
        row(f); row(f); row(f)
        assertEquals(1, keys.size); assertEquals(2, saved(f).adminRefunds.single().history.size)
    }
    @Test fun identicalOrderAndRequestIdsAreIsolatedByCustomerAccount() = runBlocking {
        val id = UUID.randomUUID().toString(); val a = fixture(id = id); val b = fixture(id = id)
        admin().decide(row(a), true, note); now += 3000; row(a)
        assertEquals(AdminRefundStatus.PENDING, row(b).status)
        assertNull(customer(b).load().single().refundTransaction)
        assertTrue(keys.single().startsWith(a.account))
    }
    @Test fun nonAdminCannotReadOrApproveRequests() = runBlocking {
        val f = fixture(); val before = row(f)
        val customer = LocalAdminRefundRepository(context, f.email, processor, { now })
        try { customer.load(); fail("Non-admin read") } catch (_: IllegalStateException) { }
        try { customer.decide(before, true, note); fail("Non-admin decision") } catch (_: IllegalStateException) { }
        assertEquals(f.order, saved(f))
    }
    @Test fun legacyOrderIsMigratedOnlyWithConfirmedRefundAndDraftIsPreserved() = runBlocking {
        val f = fixture()
        val draft = ComplaintDraft("refund-draft", description = "Nội dung nháp cần giữ nguyên")
        customer(f).saveDraft(draft)
        LocalOrderHistoryStore(context, f.email).save(listOf(f.order))
        synchronized(LocalCommerceLock) { ledger.write(ledger.read().filterNot { it.account == f.account }) }
        val pending = row(f); assertEquals(f.order, pending.order)
        admin().decide(pending, true, note)
        assertEquals(AdminRefundStatus.PROCESSING, saved(f).adminRefunds.single().status)
        assertEquals(draft, customer(f).loadDraft())
    }
}
