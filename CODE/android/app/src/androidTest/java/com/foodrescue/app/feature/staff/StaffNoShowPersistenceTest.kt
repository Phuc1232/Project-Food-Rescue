package com.foodrescue.app.feature.staff

import android.content.*
import androidx.test.platform.app.InstrumentationRegistry
import com.foodrescue.app.feature.menu.domain.MenuUnit
import com.foodrescue.app.feature.order.data.*
import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.payment.domain.*
import com.foodrescue.app.feature.staff.data.*
import com.foodrescue.app.feature.staff.domain.*
import com.foodrescue.app.feature.store.data.LocalOwnerStoreRepository
import com.foodrescue.app.feature.surprisebox.data.*
import com.foodrescue.app.feature.surprisebox.domain.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class StaffNoShowPersistenceTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "noshow_qa_${UUID.randomUUID()}_"
    private val files = mutableSetOf<String>(); private var failWrite = false
    private val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = synchronized(files) {
            files += prefix + name
            val prefs = base.getSharedPreferences(prefix + name, mode)
            if (name != "commerce_orders.v2") prefs else object : SharedPreferences by prefs {
                override fun edit(): SharedPreferences.Editor {
                    val editor = prefs.edit()
                    return object : SharedPreferences.Editor by editor {
                        override fun putString(key: String?, value: String?): SharedPreferences.Editor { editor.putString(key, value); return this }
                        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor { editor.putBoolean(key, value); return this }
                        override fun commit(): Boolean { val committed = editor.commit(); return committed && !failWrite }
                    }
                }
            }
        }
    }
    private val directory = LocalEmployeeDirectory(context)
    private val employees = LocalEmployeeRepository("owner@foodrescue.vn", 7, LocalOwnerStoreRepository(context, "owner@foodrescue.vn"), directory)
    private val inventory = LocalPackedBoxStore(context); private val ledger = LocalOrderLedger(context)
    private val box = PackedBox("b", 7, "batch", "Hộp bánh", 60000, "Sữa", listOf(PackedItem("i", "Bánh", MenuUnit.PIECE, 30000, "2", 60000)), 500, BoxStorage.AMBIENT, "p", "Bình", 100)
    private val order = BoxReservation("o", 1, 1, PickupWindow(200, 400), 1, 100, 25000, QrPayment("FR123456", 25000, PaymentStatus.SUCCEEDED, 50),
        boxSnapshot = CatalogBox(1, "Tiệm", BoxCategory.BAKERY, "Hộp", 25000, 60000, 5, "", "", "Sữa", "", 0, 18, "AB", storeId = 7, batchId = "batch"))
    private val draft = NoShowDraft("n", "o", listOf("b"), "Đã kiểm tra", true, true)
    private fun repo() = LocalStaffNoShowRepository(context, "an@example.com", directory) { 400 }
    private suspend fun setup() {
        employees.save(EmployeeDraft(name = "Nguyễn An", email = "an@example.com", permissions = pickupPermissions + EmployeePermission.PREPARE_BOXES))
        inventory.append(box); ledger.write(listOf(LocalOrderLedger.Row("customer", order)))
    }
    @After fun cleanup() { files.toList().forEach { base.deleteSharedPreferences(it) } }
    @Test fun receiptAndHoldSurviveRestartAndIdenticalRetry() = runBlocking {
        setup(); val stock = ledger.stock(7, "batch", 400); val result = repo().confirm(draft)
        assertEquals(result, repo().confirm(draft)); assertEquals(result, repo().load().orders.single())
        assertEquals(BoxCondition.RECHECK_REQUIRED, inventory.load(7).single().conditionAt(400))
        assertEquals(1, inventory.packedStock(7, "batch", 400).recheck); assertEquals(stock, ledger.stock(7, "batch", 400))
        assertTrue(runCatching { repo().confirm(draft.copy(note = "Khác")) }.isFailure)
    }
    @Test fun stalePreNoShowInspectionCannotClearHoldButNewContextCan() = runBlocking {
        setup(); val inspectionRepo = LocalStaffBoxConditionRepository(context, "an@example.com", directory) { 401 }
        val old = BoxInspectionDraft("check", "b", result = InspectionResult.PASSED, foodChecked = true, packagingChecked = true, storageChecked = true, allergensChecked = true)
        repo().confirm(draft); assertTrue(runCatching { inspectionRepo.inspect(old) }.isFailure)
        val checked = inspectionRepo.inspect(old.copy(noShowRequestId = "n"))
        assertTrue(checked.isEligibleAt(401)); assertEquals("n", LocalPackedBoxStore(context).load(7).single().inspections.single().noShowRequestId)
        assertEquals(1, ledger.stock(7, "batch", 401).sold); assertNull(ledger.read().single().order.refund)
    }
    @Test fun twoOrdersCannotClaimTheSamePhysicalBox() = runBlocking {
        setup(); ledger.write(listOf(LocalOrderLedger.Row("a", order), LocalOrderLedger.Row("b", order.copy(id = "o2"))))
        val results = listOf(draft, draft.copy(requestId = "n2", orderId = "o2")).map { request ->
            async(Dispatchers.Default) { runCatching { repo().confirm(request) }.isSuccess }
        }.awaitAll()
        assertEquals(1, results.count { it }); assertEquals(1, ledger.read().count { it.order.noShow != null })
    }
    @Test fun failedCommitRollsBackPublishedMemoryAndHold() = runBlocking {
        setup(); failWrite = true; assertTrue(runCatching { repo().confirm(draft) }.isFailure)
        assertNull(ledger.read().single().order.noShow); assertNull(inventory.load(7).single().noShow)
        failWrite = false; assertNotNull(repo().confirm(draft).noShow)
    }
    @Test fun revocationAndNewIncidentAreCheckedAgainAtCommit() = runBlocking {
        setup(); val member = employees.load().single(); employees.changeAccess(member.id, member.revision, EmployeeStatus.REVOKED, "Hết ca")
        assertTrue(runCatching { repo().confirm(draft) }.isFailure); assertTrue(repo().load().orders.isEmpty())
        val revoked = employees.load().single(); employees.changeAccess(revoked.id, revoked.revision, EmployeeStatus.ACTIVE, "")
        LocalStaffHandoverRepository(context, "an@example.com", directory) { 400 }.recordFailure(HandoverFailureDraft("f", "o", HandoverFailureReason.BOX_UNAVAILABLE))
        assertTrue(runCatching { repo().confirm(draft) }.isFailure)
    }
    @Test fun legacyOrdersLoadAndMalformedNoShowFailsClosed() {
        val legacy = OrderJson.write(order); assertEquals(order, OrderJson.read(legacy))
        legacy.put("noShow", "corrupt"); assertTrue(runCatching { OrderJson.read(legacy) }.isFailure)
        val oldBoxes = JSONObject(PackedBoxJson.encode(listOf(box))).apply { put("version", 2) }
        assertEquals(box, PackedBoxJson.decode(oldBoxes.toString()).single())
    }
}
