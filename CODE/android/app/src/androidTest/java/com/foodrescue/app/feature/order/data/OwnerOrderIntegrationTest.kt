package com.foodrescue.app.feature.order.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import com.foodrescue.app.core.common.GeoPoint
import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.payment.domain.*
import com.foodrescue.app.feature.store.data.*
import com.foodrescue.app.feature.surprisebox.data.*
import com.foodrescue.app.feature.surprisebox.domain.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class OwnerOrderIntegrationTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "orders_qa_${UUID.randomUUID()}_"
    private val preferences = mutableSetOf<String>()
    private val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = synchronized(preferences) {
            preferences += prefix + name; base.getSharedPreferences(prefix + name, mode)
        }
    }
    private var now = 1_800_000_000_000L
    private val ledger = LocalOrderLedger(context)
    private val directory = LocalPublicStoreRepository(context)
    private val owner = LocalOwnerStoreRepository(context, "owner@foodrescue.vn")
    private val storeId = directory.register(LocalOrderLedger.accountKey("owner@foodrescue.vn"))
    private val boxes = LocalOwnerBoxRepository(context, "owner@foodrescue.vn", owner, ledger, storeId) { now }
    private val catalog = LocalSaleCatalogRepository(context, directory::readStores, ledger) { now }
    private val ownerOrders = LocalOwnerOrderRepository(ledger, storeId, owner) { now }
    private fun customer(email: String = "customer@example.com") = SharedOrderHistoryStore(email, ledger, LocalOrderHistoryStore(context, email), catalog) { now }
    private suspend fun publish(quantity: Int = 2): CatalogBox {
        owner.updateProfile(owner.load().profile.copy(location = GeoPoint(10.77, 106.69), contactEmail = "owner@foodrescue.vn"))
        val template = boxes.saveTemplate(BoxTemplateDraft(name = "Hộp bánh cuối ngày", description = "Bánh tươi tuyển chọn trong ngày",
            allergens = "Sữa, trứng", minimumValue = "120000", price = "49000")).templates.single()
        val batch = boxes.saveBatch(SaleBatchDraft(templateId = template.id, quantity = quantity.toString(), salesStart = now - 1000,
            salesEnd = now + 1000000, pickupStart = now + 1100000, pickupEnd = now + 7200000)).batches.single()
        boxes.operate(batch.id, BatchAction.PUBLISH)
        return catalog.getBoxes().single()
    }
    private fun order(box: CatalogBox, id: String = UUID.randomUUID().toString()) = BoxReservation(id, box.id, 1,
        pickupWindowFor(box, now), now, holdDeadline(now, pickupWindowFor(box, now)), box.price, boxSnapshot = box)
    @After fun cleanup() { preferences.toList().forEach { base.deleteSharedPreferences(it) } }

    @Test fun publishedOrderFlowsAcrossCustomerOwnerStockAndRestart() = runBlocking {
        val box = publish()
        val customer = customer()
        val held = order(box)
        assertTrue(customer.reserve(held))
        assertEquals(1, boxes.load().batches.single().held)
        assertEquals(1, catalog.getBoxes().single().remaining)
        val paid = held.copy(payment = QrPayment(held.reference, held.total, PaymentStatus.SUCCEEDED, now))
        customer.save(listOf(paid))
        assertEquals(paid, ownerOrders.load().single())
        assertEquals(0, boxes.load().batches.single().held)
        assertEquals(1, boxes.load().batches.single().sold)
        assertEquals(paid, customer().load().single())
    }
    @Test fun concurrentCustomersCannotReserveTheLastBoxTwice() = runBlocking {
        val box = publish(1)
        val results = listOf("a@example.com", "b@example.com").map { email -> async(Dispatchers.Default) {
            customer(email).reserve(order(box))
        } }.awaitAll()
        assertEquals(1, results.count { it })
        assertEquals(0, catalog.getBoxes().single().remaining)
    }
    @Test fun stalePaymentCannotReviveExpiredHoldAndOversellTheLastBox() = runBlocking {
        val box = publish(1)
        val first = customer("first@example.com"); val held = order(box)
        assertTrue(first.reserve(held))
        now = held.expiresAtMillis + 1
        assertTrue(customer("second@example.com").reserve(order(box)))
        first.save(listOf(held.copy(payment = QrPayment(held.reference, held.total, PaymentStatus.SUCCEEDED, now))))
        assertFalse(first.load().single().isPaid)
        assertEquals(1, boxes.load().batches.single().held)
        assertEquals(0, boxes.load().batches.single().sold)
    }
    @Test fun pendingRequestSurvivesStaleCustomerSaveAndKeepsStock() = runBlocking {
        val box = publish()
        val customer = customer()
        val held = order(box)
        customer.reserve(held)
        val paid = held.copy(payment = QrPayment(held.reference, held.total, PaymentStatus.SUCCEEDED, now))
        customer.save(listOf(paid))
        ownerOrders.requestCancellation(OwnerCancellationDraft(paid.id, OwnerCancellationReason.OUT_OF_STOCK, "Không đủ bánh"))
        customer.save(listOf(paid))
        val restored = customer.load().single()
        assertNotNull(restored.ownerCancellation)
        assertEquals(OrderStatus.CANCELLATION_REQUESTED, restored.statusAt(now))
        assertNull(restored.refund)
        assertNull(restored.pickupCode(now))
        assertEquals(1, boxes.load().batches.single().sold)
        try { ownerOrders.requestCancellation(OwnerCancellationDraft(paid.id, OwnerCancellationReason.OTHER, "Một lý do khác")); fail() }
        catch (error: OwnerOrderException) { assertEquals(OwnerCancellationIssue.ALREADY_REQUESTED, error.issue) }
    }
    @Test fun ownerCannotReadOrCancelAnotherStoresOrder() = runBlocking {
        val box = publish()
        val customer = customer()
        val held = order(box); customer.reserve(held)
        customer.save(listOf(held.copy(payment = QrPayment(held.reference, held.total, PaymentStatus.SUCCEEDED, now))))
        val other = LocalOwnerOrderRepository(ledger, storeId - 1, owner) { now }
        assertTrue(other.load().isEmpty())
        try { other.requestCancellation(OwnerCancellationDraft(held.id, OwnerCancellationReason.OUT_OF_STOCK)); fail() }
        catch (error: OwnerOrderException) { assertEquals(OwnerCancellationIssue.NOT_ELIGIBLE, error.issue) }
        assertNull(ownerOrders.load().single().ownerCancellation)
        assertTrue(customer("other@example.com").load().isEmpty())
    }
    @Test fun expiryReleasesStockAndPauseBlocksNewBookingsWithoutErasingOrders() = runBlocking {
        val box = publish(1)
        val customer = customer(); val held = order(box); customer.reserve(held)
        now += 600001
        assertEquals(1, catalog.getBoxes().single().remaining)
        assertEquals(0, boxes.load().batches.single().held)
        boxes.operate(box.batchId!!, BatchAction.PAUSE)
        assertTrue(catalog.getBoxes().isEmpty())
        assertFalse(customer("other@example.com").reserve(order(box)))
        assertEquals(held.boxSnapshot, customer.load().single().boxSnapshot)
    }
    @Test fun soldStockLocksBatchQuantityAndSchedule() = runBlocking {
        val box = publish(2); val customer = customer()
        val held = order(box).copy(quantity = 2); customer.reserve(held)
        customer.save(listOf(held.copy(payment = QrPayment(held.reference, held.total, PaymentStatus.SUCCEEDED, now))))
        val batch = boxes.load().batches.single()
        try { boxes.saveBatch(batch.toDraft().copy(quantity = "1")); fail() } catch (_: BoxValidationException) { }
        try { boxes.saveBatch(batch.toDraft().copy(pickupEnd = batch.pickupEnd + 60000)); fail() } catch (_: BoxValidationException) { }
        assertEquals(2, boxes.load().batches.single().sold)
    }
    @Test fun legacyMigrationIsIdempotentAndKeepsOldData() = runBlocking {
        val email = "legacy@example.com"
        val legacy = LocalOrderHistoryStore(context, email)
        val old = BoxReservation("old", 1, 1, PickupWindow(now + 1000000, now + 2000000), now, now + 600000, 39000)
        legacy.save(listOf(old))
        assertEquals(listOf(old), customer(email).load())
        assertEquals(listOf(old), customer(email).load())
        assertEquals(listOf(old), legacy.load())
    }
    @Test fun malformedLedgerFailsClosedAndCannotBeOverwritten() {
        val pref = context.getSharedPreferences("commerce_orders.v2", Context.MODE_PRIVATE)
        pref.edit().putString("orders", "broken JSON").commit()
        try { customer().save(emptyList()); fail() } catch (_: org.json.JSONException) { }
        assertEquals("broken JSON", pref.getString("orders", ""))
    }
}
