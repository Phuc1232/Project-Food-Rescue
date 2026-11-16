package com.foodrescue.app.feature.staff

import android.content.*
import androidx.test.platform.app.InstrumentationRegistry
import com.foodrescue.app.feature.menu.domain.MenuUnit
import com.foodrescue.app.feature.order.data.LocalOrderLedger
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

class StaffBoxConditionPersistenceTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "condition_qa_${UUID.randomUUID()}_"
    private val files = mutableSetOf<String>()
    private var failWrite = false
    private var publishBeforeFailure = false
    private val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = synchronized(files) {
            files += prefix + name
            val prefs = base.getSharedPreferences(prefix + name, mode)
            if (name != "commerce_packed_boxes.v1") prefs else object : SharedPreferences by prefs {
                override fun edit(): SharedPreferences.Editor {
                    val editor = prefs.edit()
                    return object : SharedPreferences.Editor by editor {
                        override fun putString(key: String?, value: String?): SharedPreferences.Editor { editor.putString(key, value); return this }
                        override fun commit(): Boolean = if (failWrite) { if (publishBeforeFailure) editor.commit(); false } else editor.commit()
                    }
                }
            }
        }
    }
    private val owner = LocalOwnerStoreRepository(context, "owner@foodrescue.vn")
    private val directory = LocalEmployeeDirectory(context)
    private val employees = LocalEmployeeRepository("owner@foodrescue.vn", 7, owner, directory)
    private val inventory = LocalPackedBoxStore(context)
    private val ledger = LocalOrderLedger(context)
    private val box = PackedBox("b", 7, "batch", "Hộp bánh", 60000, "Sữa", listOf(PackedItem("i", "Bánh", MenuUnit.PIECE, 30000, "2", 60000)), 500, BoxStorage.AMBIENT, "p", "Bình", 100)
    private val order = BoxReservation("o", 1, 1, PickupWindow(200, 400), 1, 100, 25000, QrPayment("FR123456", 25000, PaymentStatus.SUCCEEDED, 50),
        boxSnapshot = CatalogBox(1, "Tiệm", BoxCategory.BAKERY, "Hộp", 25000, 60000, 5, "", "", "Sữa", "", 0, 18, "AB", storeId = 7, batchId = "batch"))
    private fun repo(email: String = "an@example.com") = LocalStaffBoxConditionRepository(context, email, directory) { 250 }
    private fun reject() = BoxInspectionDraft("reject", box.id, 0, InspectionResult.REJECTED, InspectionReason.PACKAGING, "Bao bì rách")
    private suspend fun setup() {
        employees.save(EmployeeDraft(name = "Nguyễn An", email = "an@example.com", permissions = pickupPermissions + EmployeePermission.PREPARE_BOXES))
        inventory.append(box)
        ledger.write(listOf(LocalOrderLedger.Row("customer", order)))
    }
    @After fun cleanup() { files.toList().forEach { base.deleteSharedPreferences(it) } }
    @Test fun rejectedHistorySurvivesRestartAndRetryWithoutChangingSaleLedger() = runBlocking {
        setup(); val original = ledger.read(); val saved = repo().inspect(reject())
        assertEquals(saved, repo().inspect(reject())); assertEquals(saved, repo().load().boxes.single())
        assertEquals(1, saved.inspections.size); assertEquals(PackedBoxStock(1, 0, 0, 1, 0), inventory.packedStock(7, "batch", 250))
        assertEquals(original, ledger.read())
        assertTrue(runCatching { repo().inspect(reject().copy(note = "Khác")) }.isFailure)
    }
    @Test fun oldLedgerLoadsAndCorruptHistoryFailsClosed() {
        val old = JSONObject(PackedBoxJson.encode(listOf(box))).apply { put("version", 1); getJSONArray("boxes").getJSONObject(0).remove("inspections") }
        assertEquals(box, PackedBoxJson.decode(old.toString()).single())
        old.put("version", 2); assertTrue(runCatching { PackedBoxJson.decode(old.toString()) }.isFailure)
        old.put("version", 99); assertTrue(runCatching { PackedBoxJson.decode(old.toString()) }.isFailure)
    }
    @Test fun expiredAndRejectedCountsAreDisjointAndDeliveredKeepsItsHistory() = runBlocking {
        setup(); repo().inspect(reject()); inventory.append(box.copy(id = "expired", expiresAt = 200)); inventory.append(box.copy(id = "delivered"))
        val completed = order.copy(collectedAtMillis = 220, handover = HandoverReceipt("h", listOf("delivered"), "e", "An", 220))
        ledger.write(listOf(LocalOrderLedger.Row("customer", completed)))
        assertEquals(PackedBoxStock(3, 0, 1, 1, 1), inventory.packedStock(7, "batch", 250))
    }
    @Test fun permissionRevocationAndStoreBoundariesAreRevalidatedAtCommit() = runBlocking {
        setup(); val member = employees.load().single()
        employees.changeAccess(member.id, member.revision, EmployeeStatus.REVOKED, "Hết ca")
        assertTrue(runCatching { repo().inspect(reject()) }.isFailure); assertTrue(repo().load().boxes.isEmpty())
        assertTrue(runCatching { repo("unknown@example.com").inspect(reject()) }.isFailure)
        val revoked = employees.load().single(); employees.changeAccess(revoked.id, revoked.revision, EmployeeStatus.ACTIVE, "")
        inventory.append(box.copy(id = "other-store", storeId = 8))
        assertTrue(runCatching { repo().inspect(reject().copy(boxId = "other-store")) }.isFailure)
    }
    @Test fun staleInspectionAndDuplicateRequestCannotOverwriteAudit() = runBlocking {
        setup()
        val pass = BoxInspectionDraft("pass", box.id, 0, InspectionResult.PASSED, foodChecked = true, packagingChecked = true, storageChecked = true, allergensChecked = true)
        val results = listOf(pass, reject()).map { draft -> async(Dispatchers.Default) { runCatching { repo().inspect(draft) }.isSuccess } }.awaitAll()
        assertEquals(1, results.count { it }); assertEquals(1, inventory.load(7).single().inspections.size)
    }
    @Test fun rejectionAndHandoverCannotBothCommit() = runBlocking {
        setup(); val handover = LocalStaffHandoverRepository(context, "an@example.com", directory) { 250 }
        val results = listOf(async(Dispatchers.Default) { runCatching { repo().inspect(reject()) }.isSuccess },
            async(Dispatchers.Default) { runCatching { handover.complete(HandoverDraft("handover", "o", "FR123456", listOf("b"))) }.isSuccess }).awaitAll()
        assertEquals(1, results.count { it })
        val current = inventory.load(7).single()
        assertFalse(current.handover != null && current.inspections.any { it.result == InspectionResult.REJECTED })
    }
    @Test fun failedWriteDoesNotReportOrStoreAnInspection() = runBlocking {
        setup(); failWrite = true
        assertTrue(runCatching { repo().inspect(reject()) }.isFailure)
        assertTrue(inventory.load(7).single().inspections.isEmpty())
        failWrite = false; assertEquals(1, repo().inspect(reject()).inspections.size)
    }
    @Test fun failedCommitCannotLeaveAnUnconfirmedInspectionVisibleOnRefresh() = runBlocking {
        setup(); failWrite = true; publishBeforeFailure = true
        assertTrue(runCatching { repo().inspect(reject()) }.isFailure)
        assertTrue("A failed commit must not appear saved on refresh", repo().load().boxes.single().inspections.isEmpty())
        failWrite = false; assertEquals(1, repo().inspect(reject()).inspections.size)
    }
}
