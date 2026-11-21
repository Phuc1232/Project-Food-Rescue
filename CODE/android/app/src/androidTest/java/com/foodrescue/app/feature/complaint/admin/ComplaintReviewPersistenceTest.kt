package com.foodrescue.app.feature.complaint.admin

import android.content.Context
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.foodrescue.app.core.datastore.LocalCommerceLock
import com.foodrescue.app.feature.complaint.data.*
import com.foodrescue.app.feature.complaint.domain.*
import com.foodrescue.app.feature.order.data.*
import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.payment.domain.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class ComplaintReviewPersistenceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val accounts = mutableListOf<String>()
    private val files = mutableListOf<File>()
    private val note = "Đã kiểm tra bằng chứng và xác nhận hộp thiếu sản phẩm theo cam kết."
    private val admin get() = LocalComplaintReviewRepository(context, " ADMIN@FOODRESCUE.VN ") { 20_000 }
    private data class Fixture(val email: String, val account: String, val order: BoxReservation, val item: CustomerComplaint)

    private fun fixture(id: String = UUID.randomUUID().toString(), withEvidence: Boolean = false): Fixture {
        val email = "admin-review-${UUID.randomUUID()}@example.com"
        val account = complaintAccountKey(email); accounts += account
        val order = BoxReservation("order-$id", 1, 2, PickupWindow(10_000, 20_000), 1000, 9000, 30_000,
            QrPayment("FR$id", 60_000, PaymentStatus.SUCCEEDED, 2000), collectedAtMillis = 11_000)
        val evidence = if (withEvidence) {
            val imageId = UUID.randomUUID().toString()
            val dir = File(context.filesDir, "complaint-evidence/$account").apply { mkdirs() }
            val preview = File(dir, "$imageId.jpg"); files += preview
            val bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
            preview.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }; bitmap.recycle()
            listOf(LocalEvidenceStore(context, email).restore(imageId, "Bằng chứng.jpg", "image/jpeg", preview.length()))
        } else emptyList()
        val item = CustomerComplaint("KN$id", ComplaintOrder(order.id, order.reference, "Tiệm Bánh Mộc", "Hộp bánh", 2, 60_000, 1000),
            ComplaintReason.MISSING_ITEMS, "Số lượng sản phẩm trong hộp không đúng mô tả.", evidence, ComplaintStatus.PENDING, 12_000, 12_000)
        synchronized(LocalCommerceLock) {
            val ledger = LocalOrderLedger(context)
            ledger.write(ledger.read() + LocalOrderLedger.Row(account, order))
        }
        assertTrue(LocalComplaintRepository(context, email, LocalEvidenceStore(context, email)) { true }.submit(item))
        return Fixture(email, account, order, item)
    }
    private fun customer(fixture: Fixture) = LocalComplaintRepository(context, fixture.email, LocalEvidenceStore(context, fixture.email)) { true }
    private suspend fun row(fixture: Fixture) = admin.load().single { it.accountKey == fixture.account && it.complaint.id == fixture.item.id }

    @After fun cleanup() {
        synchronized(LocalCommerceLock) {
            val ledger = LocalOrderLedger(context)
            ledger.write(ledger.read().filterNot { it.account in accounts })
            accounts.forEach { context.deleteSharedPreferences("customer_complaints_$it"); context.deleteSharedPreferences("customer_orders_$it") }
        }
        files.forEach { it.delete() }
        accounts.forEach { File(context.filesDir, "complaint-evidence/$it").delete() }
    }

    @Test fun legacySubmissionDecisionAndDraftSurviveAcrossRoles() = runBlocking {
        val fixture = fixture(withEvidence = true)
        val prefs = context.getSharedPreferences("customer_complaints_${fixture.account}", Context.MODE_PRIVATE)
        val raw = JSONArray(prefs.getString("cases.v1", "[]"))
        raw.getJSONObject(0).apply { remove("revision"); remove("history") }
        assertTrue(prefs.edit().putString("cases.v1", raw.toString()).commit())
        val draft = ComplaintDraft("draft", description = "Nội dung nháp khác của khách hàng")
        customer(fixture).saveDraft(draft)
        val before = row(fixture)
        assertEquals(0L, before.complaint.revision)
        assertTrue(before.complaint.evidence.single().previewUri.contains(fixture.account))
        val received = admin.review(before, ComplaintReviewAction.RECEIVED, "", 0)
        val result = admin.review(received, ComplaintReviewAction.RESOLVED, note, 0)
        assertEquals(result.complaint, customer(fixture).load().single())
        assertEquals(draft, customer(fixture).loadDraft())
        assertEquals(fixture.item.evidence, result.complaint.evidence)
        assertEquals(2, result.complaint.history.size)
    }
    @Test fun duplicateIdsAcrossAccountsNeverExposeOrMutateOtherCustomer() = runBlocking {
        val id = UUID.randomUUID().toString()
        val first = fixture(id, true); val second = fixture(id, true)
        val received = admin.review(row(first), ComplaintReviewAction.RECEIVED, "", 0)
        admin.review(received, ComplaintReviewAction.REJECTED, note, 0)
        assertEquals(ComplaintStatus.PENDING, customer(second).load().single().status)
        assertEquals(first.item.evidence.single().previewUri, row(first).complaint.evidence.single().previewUri)
        assertNotEquals(row(first).complaint.evidence.single().previewUri, row(second).complaint.evidence.single().previewUri)
        try { LocalComplaintReviewRepository(context, "customer@example.com").load(); fail("Non-admin access") }
        catch (_: IllegalStateException) { }
    }
    @Test fun legacyCustomerHistoryIsUsedUntilSharedLedgerHasAuthoritativeOrder() = runBlocking {
        val fixture = fixture()
        LocalOrderHistoryStore(context, fixture.email).save(listOf(fixture.order))
        synchronized(LocalCommerceLock) {
            val ledger = LocalOrderLedger(context)
            ledger.write(ledger.read().filterNot { it.account == fixture.account })
        }
        assertEquals(fixture.order, row(fixture).order)
        val received = admin.review(row(fixture), ComplaintReviewAction.RECEIVED, "", 0)
        val changed = fixture.order.copy(payment = fixture.order.payment!!.copy(updatedAtMillis = 3000))
        synchronized(LocalCommerceLock) {
            val ledger = LocalOrderLedger(context)
            ledger.write(ledger.read() + LocalOrderLedger.Row(fixture.account, changed))
        }
        assertEquals(changed, row(fixture).order)
        try { admin.review(received, ComplaintReviewAction.REFUND_REQUESTED, note, 20_000); fail("Stale legacy order") }
        catch (_: ChangedComplaintOrder) { }
        assertNull(customer(fixture).load().single().refundRequest)
    }
    @Test fun changedOrderAndStaleCaseAreRejectedWithoutLostUpdates() = runBlocking {
        val fixture = fixture()
        val pending = row(fixture)
        val received = admin.review(pending, ComplaintReviewAction.RECEIVED, "", 0)
        try { admin.review(pending, ComplaintReviewAction.RECEIVED, "", 0); fail("Duplicate receipt") }
        catch (_: StaleComplaintReview) { }
        synchronized(LocalCommerceLock) {
            val ledger = LocalOrderLedger(context)
            ledger.write(ledger.read().map { if (it.account == fixture.account) it.copy(order = it.order.copy(payment = it.order.payment!!.copy(updatedAtMillis = 3000))) else it })
        }
        try { admin.review(received, ComplaintReviewAction.REFUND_REQUESTED, note, 20_000); fail("Stale payment") }
        catch (_: ChangedComplaintOrder) { }
        assertEquals(ComplaintStatus.IN_REVIEW, customer(fixture).load().single().status)
        assertNull(customer(fixture).load().single().refundRequest)
    }
    @Test fun competingDecisionsCommitExactlyOneHistoryAndRefund() = runBlocking {
        val fixture = fixture()
        val received = admin.review(row(fixture), ComplaintReviewAction.RECEIVED, "", 0)
        val outcomes = listOf(ComplaintReviewAction.REJECTED, ComplaintReviewAction.REFUND_REQUESTED).map { action -> async(Dispatchers.IO) {
            try { admin.review(received, action, note, if (action == ComplaintReviewAction.REFUND_REQUESTED) 20_000 else 0); true }
            catch (_: StaleComplaintReview) { false }
        } }.awaitAll()
        assertEquals(1, outcomes.count { it })
        assertEquals(2, customer(fixture).load().single().history.size)
        assertEquals(2L, customer(fixture).load().single().revision)
    }
    @Test fun queuedRefundKeepsSettlementHoldAndCannotSpendAmountTwice() = runBlocking {
        val fixture = fixture()
        val received = admin.review(row(fixture), ComplaintReviewAction.RECEIVED, "", 0)
        val queued = admin.review(received, ComplaintReviewAction.REFUND_REQUESTED, note, 40_000)
        assertEquals(20_000L, queued.refundableAmount)
        assertEquals(40_000L, customer(fixture).load().single().refundRequest!!.amount)
        assertEquals(setOf(fixture.order.id), LocalComplaintHolds(context).read(fixture.account, setOf(fixture.order.id)))
        assertEquals(fixture.order, row(fixture).order)
        val next = fixture.item.copy(id = fixture.item.id + "-next")
        assertTrue(customer(fixture).submit(next))
        val nextRow = admin.load().single { it.accountKey == fixture.account && it.complaint.id == next.id }
        val nextReceived = admin.review(nextRow, ComplaintReviewAction.RECEIVED, "", 0)
        try { admin.review(nextReceived, ComplaintReviewAction.REFUND_REQUESTED, note, 20_001); fail("Over-refund") }
        catch (_: IllegalArgumentException) { }
        assertEquals(1, customer(fixture).load().count { it.refundRequest != null })
    }
    @Test fun corruptAccountBlocksReviewInsteadOfSilentlyDroppingRecords() = runBlocking {
        val fixture = fixture()
        val before = row(fixture)
        val prefs = context.getSharedPreferences("customer_complaints_${fixture.account}", Context.MODE_PRIVATE)
        prefs.edit().putString("cases.v1", "broken").commit()
        try { admin.review(before, ComplaintReviewAction.RECEIVED, "", 0); fail("Corrupt account") }
        catch (_: org.json.JSONException) { }
        assertEquals("broken", prefs.getString("cases.v1", null))
        assertFalse(customer(fixture).submit(fixture.item.copy(id = "another")))
    }
}
