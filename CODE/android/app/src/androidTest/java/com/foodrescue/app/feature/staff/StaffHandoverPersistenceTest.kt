package com.foodrescue.app.feature.staff

import android.content.*
import androidx.test.platform.app.InstrumentationRegistry
import com.foodrescue.app.feature.discovery.domain.CatalogRepository
import com.foodrescue.app.feature.menu.domain.MenuUnit
import com.foodrescue.app.feature.order.data.*
import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.payment.domain.*
import com.foodrescue.app.feature.staff.data.*
import com.foodrescue.app.feature.staff.domain.*
import com.foodrescue.app.feature.store.data.LocalOwnerStoreRepository
import com.foodrescue.app.feature.surprisebox.data.LocalPackedBoxStore
import com.foodrescue.app.feature.surprisebox.domain.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class StaffHandoverPersistenceTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "handover_qa_${UUID.randomUUID()}_"
    private val files = mutableSetOf<String>()
    private val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = synchronized(files) {
            files += prefix + name; base.getSharedPreferences(prefix + name, mode)
        }
    }
    private val owner = LocalOwnerStoreRepository(context, "owner@foodrescue.vn")
    private val directory = LocalEmployeeDirectory(context)
    private val employees = LocalEmployeeRepository("owner@foodrescue.vn", 7, owner, directory)
    private val ledger = LocalOrderLedger(context)
    private val inventory = LocalPackedBoxStore(context)
    private val catalogBox = CatalogBox(1, "Tiệm bánh", BoxCategory.BAKERY, "Hộp bánh", 25000, 60000, 5, "", "", "Sữa", "", 0, 18, "AB", storeId = 7, batchId = "batch")
    private val order = BoxReservation("o", 1, 1, PickupWindow(200, 400), 1, 100, 25000,
        QrPayment("FR123456", 25000, PaymentStatus.SUCCEEDED, 50), boxSnapshot = catalogBox)
    private val box = PackedBox("b", 7, "batch", "Hộp bánh", 60000, "Sữa",
        listOf(PackedItem("i", "Bánh", MenuUnit.PIECE, 30000, "2", 60000)), 500, BoxStorage.AMBIENT, "p", "Bình", 150)
    private fun repo(email: String = "an@example.com") = LocalStaffHandoverRepository(context, email, directory) { 250 }
    private suspend fun setup() {
        employees.save(EmployeeDraft(name = "Nguyễn An", email = "an@example.com", permissions = pickupPermissions))
        ledger.write(listOf(LocalOrderLedger.Row(LocalOrderLedger.accountKey("customer@example.com"), order)))
        inventory.append(box)
    }
    private fun draft(orderId: String = "o") = HandoverDraft("request-$orderId", orderId, "FR123456", listOf("b"))
    private fun customer() = SharedOrderHistoryStore("customer@example.com", ledger,
        LocalOrderHistoryStore(context, "customer@example.com"), object : CatalogRepository { override fun getBoxes() = listOf(catalogBox) }) { 250 }
    @After fun cleanup() { files.toList().forEach { base.deleteSharedPreferences(it) } }
    @Test fun receiptSurvivesRestartAndUpdatesOwnerCustomerAndPhysicalInventoryTogether() = runBlocking {
        setup(); val done = repo().complete(draft())
        assertEquals(done, customer().load().single())
        assertEquals(done, LocalOwnerOrderRepository(ledger, 7, owner) { 250 }.load().single())
        assertEquals(done, repo().load().orders.single())
        assertEquals(OrderStatus.COLLECTED, done.statusAt(250))
        assertEquals("o", inventory.load(7).single().handover!!.orderId)
        assertEquals(PackedBoxStock(1, 0, 1), inventory.packedStock(7, "batch", 250))
        customer().save(listOf(order)); assertEquals(done, customer().load().single())
        assertEquals(done, repo().complete(draft()))
    }
    @Test fun concurrentOrdersCannotReceiveTheSamePhysicalBox() = runBlocking {
        setup(); ledger.write(ledger.read() + LocalOrderLedger.Row("another", order.copy(id = "other")))
        val results = listOf("o", "other").map { id -> async(Dispatchers.Default) { runCatching { repo().complete(draft(id)) }.isSuccess } }.awaitAll()
        assertEquals(1, results.count { it }); assertEquals(1, ledger.read().count { it.order.collectedAtMillis != null })
    }
    @Test fun revokedPermissionAndCancellationAreRevalidatedAtCommit() = runBlocking {
        setup(); val member = employees.load().single()
        employees.changeAccess(member.id, member.revision, EmployeeStatus.REVOKED, "Đã hết ca")
        assertTrue(runCatching { repo().complete(draft()) }.isFailure)
        assertTrue(repo().load().orders.isEmpty())
        val revoked = employees.load().single(); employees.changeAccess(revoked.id, revoked.revision, EmployeeStatus.ACTIVE, "")
        LocalOwnerOrderRepository(ledger, 7, owner) { 250 }.requestCancellation(OwnerCancellationDraft("o", OwnerCancellationReason.OUT_OF_STOCK))
        assertTrue(runCatching { repo().complete(draft()) }.isFailure)
        assertNull(inventory.load(7).single().handover)
    }
    @Test fun failedHandoverIsIdempotentAndSurvivesCustomerRefreshWithoutCancelling() = runBlocking {
        setup(); val failure = HandoverFailureDraft("failed", "o", HandoverFailureReason.CODE_MISMATCH)
        val saved = repo().recordFailure(failure); assertEquals(saved, repo().recordFailure(failure))
        customer().save(listOf(order)); val restored = customer().load().single()
        assertEquals(1, restored.handoverFailures.size); assertNull(restored.collectedAtMillis); assertNull(restored.cancelledAtMillis)
        assertEquals(1, inventory.packedStock(7, "batch", 250).eligible)
        assertNotNull(repo().complete(draft()).handover)
    }
    @Test fun missingMembershipAndDifferentStoreCannotAccessOrders() = runBlocking {
        setup(); assertTrue(repo("unknown@example.com").load().orders.isEmpty())
        assertTrue(runCatching { repo("unknown@example.com").complete(draft()) }.isFailure)
        ledger.write(listOf(LocalOrderLedger.Row("other", order.copy(boxSnapshot = catalogBox.copy(storeId = 8)))))
        assertTrue(repo().load().orders.isEmpty()); assertTrue(runCatching { repo().complete(draft()) }.isFailure)
    }
    @Test fun handoverOnlyEmployeeCanReadPhysicalInventoryButCannotPack() = runBlocking {
        setup()
        val packing = LocalStaffPackingRepository(context, "an@example.com", directory) { 250 }
        assertEquals(listOf(box), packing.load().boxes)
        assertTrue(runCatching { packing.complete(PackingDraft("new", "batch")) }.isFailure)
        assertNotNull(repo().complete(draft()).handover)
        assertNotNull(packing.load().boxes.single().handover)
    }
}
