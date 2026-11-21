package com.foodrescue.app.feature.store

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import com.foodrescue.app.core.common.GeoPoint
import com.foodrescue.app.feature.menu.data.*
import com.foodrescue.app.feature.menu.domain.*
import com.foodrescue.app.feature.order.data.*
import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.payment.domain.*
import com.foodrescue.app.feature.store.data.*
import com.foodrescue.app.feature.store.domain.*
import com.foodrescue.app.feature.surprisebox.data.*
import com.foodrescue.app.feature.surprisebox.domain.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Every repository receives a namespaced application context; real phone records are never fixture storage. */
class StoreSupervisionPersistenceTest {
    private class IsolatedContext(base: Context) : ContextWrapper(base) {
        private val prefix = "supervision_test_${UUID.randomUUID()}_"
        private val names = mutableSetOf<String>()
        var failNextStoreWrite = false
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = File(baseContext.filesDir, prefix).apply { mkdirs() }
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            synchronized(names) { names += prefix + name }
            val actual = baseContext.getSharedPreferences(prefix + name, mode)
            if (!name.startsWith("owner_store_") || name == "owner_store_directory.v1") return actual
            return object : SharedPreferences by actual {
                override fun edit(): SharedPreferences.Editor {
                    val editor = actual.edit()
                    return object : SharedPreferences.Editor by editor {
                        override fun putString(key: String?, value: String?): SharedPreferences.Editor { editor.putString(key, value); return this }
                        override fun commit(): Boolean {
                            val result = editor.commit()
                            if (failNextStoreWrite) { failNextStoreWrite = false; return false }
                            return result
                        }
                    }
                }
            }
        }
        fun cleanup() {
            names.forEach { name -> baseContext.getSharedPreferences(name, MODE_PRIVATE).edit().clear().commit(); baseContext.deleteSharedPreferences(name) }
            File(baseContext.filesDir, prefix).deleteRecursively()
        }
    }
    private val context = IsolatedContext(InstrumentationRegistry.getInstrumentation().targetContext)
    private val email = "supervision@example.com"
    private val key = LocalStoreRecords.accountKey(email)
    private val records = LocalStoreRecords(context)
    private val directory = LocalPublicStoreRepository(context)
    private val ledger = LocalOrderLedger(context)
    private val owner = LocalOwnerStoreRepository(context, email)
    private val now = 20_000L
    private val note = "Cần đối chiếu lại giá trị hộp và chất lượng thực phẩm."
    private val template = BoxTemplate("template", "Hộp bánh", BoxCategory.BAKERY, "Bánh tươi", "Sữa", 90_000, 30_000)
    private val batch = SaleBatch("batch", template, 10, 1000, 50_000, 60_000, 90_000, BatchControl.PUBLISHED)
    private val other = batch.copy(id = "other")
    private val admin get() = LocalStoreSupervisionRepository(context, "admin@foodrescue.vn") { now }
    private val sales get() = LocalSaleCatalogRepository(context, directory::readStores, ledger) { now }
    private val storeId get() = directory.registeredAccounts().getValue(key)
    private fun fixture() {
        val profile = OwnerStoreProfile("Bánh Mộc", "Tiệm bánh", "Nguyễn An", "0901234567", "47 Nguyễn Thượng Hiền", "Bàn Cờ", location = GeoPoint(10.777, 106.699))
        records.change(key, { OwnerStore() }) { OwnerStore(profile, StoreReviewStatus.APPROVED) }
        saveCatalog(OwnerBoxCatalog(listOf(template), listOf(batch, other)))
    }
    private fun saveCatalog(catalog: OwnerBoxCatalog) { check(context.getSharedPreferences("owner_boxes_$key", Context.MODE_PRIVATE).edit().putString("catalog.v1", OwnerBoxJson.encode(catalog)).commit()) }
    private suspend fun decide(action: StoreRestrictionAction, target: SaleBatch? = null) = admin.decide(key,
        StoreRestrictionDraft(UUID.randomUUID().toString(), owner.load().revision, action, target, note))
    private fun order(box: CatalogBox, id: String, paid: Boolean = false) = BoxReservation(id, box.id, 1,
        PickupWindow(60_000, 90_000), 1000, 30_000, 30_000,
        if (paid) QrPayment("FR$id", 30_000, PaymentStatus.SUCCEEDED, 10_000) else null, boxSnapshot = box)
    private fun customer() = SharedOrderHistoryStore("customer@example.com", ledger,
        LocalOrderHistoryStore(context, "customer@example.com"), sales) { now }
    @After fun cleanup() { context.cleanup() }

    @Test fun suspensionHidesMapAndSalesAndBlocksFreshReservationsButPreservesPaidOrdersAndReceipts() = runBlocking {
        fixture(); val box = sales.getBoxes().first { it.batchId == batch.id }
        val paid = order(box, "paid", true); val shared = customer()
        assertTrue(shared.reserve(paid))
        val packed = PackedBox("physical", storeId, batch.id, template.name, 90_000, "Sữa",
            listOf(PackedItem("bread", "Bánh", MenuUnit.PIECE, 45_000, "2", 90_000)), 100_000, BoxStorage.AMBIENT, "staff", "An", 5000)
        LocalPackedBoxStore(context).append(packed)
        val beforeOrders = ledger.read(); val beforeBoxes = LocalPackedBoxStore(context).load(storeId)
        decide(StoreRestrictionAction.SUSPEND_STORE)
        assertTrue(directory.getStores().isEmpty()); assertTrue(sales.getBoxes().isEmpty())
        assertFalse(shared.reserve(order(box, "new")))
        assertEquals(beforeOrders, ledger.read()); assertEquals(beforeBoxes, LocalPackedBoxStore(context).load(storeId))
        assertEquals(StoreReviewStatus.APPROVED, owner.load().status)
        assertEquals(1, admin.detail(key).paidOrders)
    }
    @Test fun existingHoldCanCompletePaymentAfterRestrictionWithoutCreatingANewReservation() = runBlocking {
        fixture(); val box = sales.getBoxes().first(); val shared = customer(); val held = order(box, "held")
        assertTrue(shared.reserve(held)); decide(StoreRestrictionAction.SUSPEND_STORE)
        shared.save(listOf(held.copy(payment = QrPayment("FRheld", 30_000, PaymentStatus.SUCCEEDED, now))))
        assertTrue(shared.load().single().isPaid); assertFalse(shared.reserve(order(box, "new")))
    }
    @Test fun batchRestrictionIsScopedAndOwnerCannotPublishOrResumeIt() = runBlocking {
        fixture(); decide(StoreRestrictionAction.STOP_BATCH, batch)
        assertEquals(listOf(other.id), sales.getBoxes().map { it.batchId }); assertEquals(1, directory.getStores().size)
        val repository = LocalOwnerBoxRepository(context, email, owner, ledger, storeId, clock = { now })
        assertTrue(runCatching { repository.operate(batch.id, BatchAction.RESUME) }.exceptionOrNull() is RestrictedStoreSale)
        assertEquals(BatchControl.PUBLISHED, repository.load().batches.first { it.id == batch.id }.control)
        decide(StoreRestrictionAction.RELEASE_BATCH, batch)
        assertEquals(2, sales.getBoxes().size)
    }
    @Test fun liftingRestrictionsDoesNotReopenOwnerPausedOrEndedBatches() = runBlocking {
        fixture(); decide(StoreRestrictionAction.STOP_BATCH, batch)
        saveCatalog(OwnerBoxCatalog(listOf(template), listOf(batch.copy(control = BatchControl.PAUSED), other.copy(control = BatchControl.ENDED))))
        decide(StoreRestrictionAction.RELEASE_BATCH, batch.copy(control = BatchControl.PAUSED))
        assertTrue(sales.getBoxes().isEmpty())
    }
    @Test fun restoringStorePreservesBatchBlockAndProfileUpdatesCannotEraseRestrictions() = runBlocking {
        fixture(); decide(StoreRestrictionAction.STOP_BATCH, batch); decide(StoreRestrictionAction.SUSPEND_STORE)
        owner.updateProfile(owner.load().profile.copy(description = "Đã cập nhật thông tin"))
        assertNotNull(owner.load().governance.suspension)
        decide(StoreRestrictionAction.RESTORE_STORE)
        assertEquals(listOf(other.id), sales.getBoxes().map { it.batchId }); assertEquals(1, directory.getStores().size)
        assertEquals(3, owner.load().governance.events.size)
    }
    @Test fun staleStoreAndStaleBatchRequireFreshReview() = runBlocking {
        fixture(); val revision = owner.load().revision
        val draft = StoreRestrictionDraft("request", revision, StoreRestrictionAction.STOP_BATCH, batch, note)
        saveCatalog(OwnerBoxCatalog(listOf(template), listOf(batch.copy(quantity = 15), other)))
        assertTrue(runCatching { admin.decide(key, draft) }.exceptionOrNull() is StaleStoreSupervision)
        owner.updateProfile(owner.load().profile.copy(description = "Thông tin mới"))
        assertTrue(runCatching { admin.decide(key, draft.copy(batch = batch.copy(quantity = 15))) }.exceptionOrNull() is StaleStoreSupervision)
        assertTrue(owner.load().governance.events.isEmpty())
    }
    @Test fun parallelDecisionsCommitExactlyOneAuditEvent() = runBlocking {
        fixture(); val draft = StoreRestrictionDraft("request", owner.load().revision, StoreRestrictionAction.SUSPEND_STORE, note = note)
        val results = withTimeout(5000) { List(2) { async(Dispatchers.IO) { runCatching { admin.decide(key, draft) } } }.awaitAll() }
        assertEquals(1, results.count { it.isSuccess }); assertEquals(1, owner.load().governance.events.size)
        assertTrue(results.single { it.isFailure }.exceptionOrNull() is StaleStoreSupervision)
    }
    @Test fun failedCommitRollsBackMemoryAndDiskBeforeRetry() = runBlocking {
        fixture(); val before = owner.load(); context.failNextStoreWrite = true
        assertTrue(runCatching { decide(StoreRestrictionAction.SUSPEND_STORE) }.isFailure)
        assertEquals(before, owner.load()); assertEquals(2, sales.getBoxes().size)
        decide(StoreRestrictionAction.SUSPEND_STORE); assertEquals(1, owner.load().governance.events.size)
    }
    @Test fun onlyAdministratorCanReadAndWriteSupervision() = runBlocking {
        fixture(); val denied = LocalStoreSupervisionRepository(context, email)
        assertTrue(runCatching { denied.stores() }.isFailure); assertTrue(runCatching { denied.detail(key) }.isFailure)
        assertTrue(runCatching { denied.decide(key, StoreRestrictionDraft("bad", owner.load().revision, StoreRestrictionAction.SUSPEND_STORE, note = note)) }.isFailure)
        assertTrue(owner.load().governance.events.isEmpty())
    }
    @Test fun legacyJsonLoadsAndMalformedRestrictionsFailClosed() = runBlocking {
        fixture(); val original = owner.load()
        val old = JSONObject(OwnerStoreJson.encode(original)).apply { remove("restrictions") }.toString()
        assertEquals(original, OwnerStoreJson.decode(old))
        context.getSharedPreferences("owner_store_$key", Context.MODE_PRIVATE).edit()
            .putString("store.v1", JSONObject(old).put("restrictions", "invalid").toString()).commit()
        assertTrue(directory.getStores().isEmpty()); assertTrue(runCatching { admin.detail(key) }.isFailure)
    }
    @Test fun readOnlyMenuUsesOwnerCodecAndCorrectAccountAssetDirectory() = runBlocking {
        fixture(); val id = UUID.randomUUID().toString()
        val directory = File(context.filesDir, "owner-menu/$key").apply { mkdirs() }
        File(directory, "$id.source").writeBytes(byteArrayOf(1)); File(directory, "$id.jpg").writeBytes(byteArrayOf(1))
        val assets = LocalMenuAssetStore(context, email)
        val document = assets.restore(id, "Bảng giá", "image/jpeg", 1, 1)
        val item = MenuItem("bread", "Bánh sữa", MenuCategory.BAKERY, MenuUnit.PIECE, 45_000, "", MenuStatus.ACTIVE, document, 1000, 5000)
        val menu = StoreMenu(listOf(item), listOf(document))
        context.getSharedPreferences("owner_menu_$key", Context.MODE_PRIVATE).edit().putString("menu.v1", MenuJson.encode(menu)).commit()
        val detail = admin.detail(key)
        assertEquals(LocalMenuRepository(context, email, owner, assets).load(), detail.menu)
        assertEquals(document.sourceUri, detail.menu.documents.single().sourceUri)
        assertEquals(menu, detail.menu); assertTrue(detail.activity.any { it.id == "menu:bread" })
        assertTrue(File(directory, "$id.source").isFile)
    }
    @Test fun unregisteredStarterIsNotCreatedDuringRead() = runBlocking {
        assertTrue(admin.stores().isEmpty())
        assertFalse(LocalStoreRecords(context).contains(LocalStoreRecords.accountKey("owner@foodrescue.vn")))
    }
}
