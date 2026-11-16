package com.foodrescue.app.feature.staff

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import com.foodrescue.app.feature.staff.data.*
import com.foodrescue.app.feature.staff.domain.*
import com.foodrescue.app.feature.menu.data.*
import com.foodrescue.app.feature.menu.domain.*
import com.foodrescue.app.feature.store.data.*
import com.foodrescue.app.feature.surprisebox.data.*
import com.foodrescue.app.feature.surprisebox.domain.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class StaffPackingPersistenceTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "packing_qa_${UUID.randomUUID()}_"
    private val files = mutableSetOf<String>()
    private val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = synchronized(files) {
            files += prefix + name; base.getSharedPreferences(prefix + name, mode)
        }
    }
    private val email = "owner@foodrescue.vn"
    private val now = 100_000L
    private val owner = LocalOwnerStoreRepository(context, email)
    private val directory = LocalEmployeeDirectory(context)
    private val employees = LocalEmployeeRepository(email, -1, owner, directory)
    private val menu = LocalMenuRepository(context, email, owner, LocalMenuAssetStore(context, email))
    private val inventory = LocalPackedBoxStore(context)
    private val boxes = LocalOwnerBoxRepository(context, email, owner, storeId = -1, clock = { now }, packedStock = inventory)
    private fun staff() = LocalStaffPackingRepository(context, "an@example.com", directory, clock = { now })
    @After fun cleanup() { files.toList().forEach { base.deleteSharedPreferences(it) } }
    private suspend fun setup(quantity: Int = 2): PackingDraft {
        employees.save(EmployeeDraft(name = "Nguyễn An", email = "an@example.com", permissions = operationsPermissions))
        val item = menu.save(MenuDraft(name = "Bánh mì", price = "30000")).items.single()
        val template = boxes.saveTemplate(BoxTemplateDraft(name = "Hộp bánh", description = "Bánh mới trong ngày", allergens = "Lúa mì", minimumValue = "60000", price = "25000")).templates.single()
        val batch = boxes.saveBatch(SaleBatchDraft(templateId = template.id, quantity = quantity.toString(), salesStart = 1, salesEnd = 200_000, pickupStart = 200_000, pickupEnd = 300_000)).batches.single()
        boxes.operate(batch.id, BatchAction.PUBLISH)
        return PackingDraft(UUID.randomUUID().toString(), batch.id, listOf(PackingSelection(item.id, "2", item.price, item.unit, item.updatedAtMillis)), 400_000, BoxStorage.AMBIENT, true, true, true)
    }
    @Test fun completionPersistsAndOwnerReadsPhysicalCountsWithoutChangingSaleStock() = runBlocking {
        val draft = setup()
        val result = staff().complete(draft)
        assertEquals(result, staff().load().boxes.single())
        val batch = boxes.load().batches.single()
        assertEquals(1, batch.packed); assertEquals(1, batch.eligible)
        assertEquals(2, batch.remaining); assertEquals(0, batch.sold)
        assertEquals(0, inventory.packedStock(-1, batch.id, 400_001).eligible)
    }
    @Test fun retryAndConcurrentLastSlotDoNotCreateDuplicateBoxes() = runBlocking {
        val draft = setup(quantity = 1)
        val result = staff().complete(draft)
        assertEquals(result, staff().complete(draft))
        assertEquals(1, staff().load().boxes.size)
        assertTrue(runCatching { staff().complete(draft.copy(id = UUID.randomUUID().toString())) }.isFailure)
    }
    @Test fun twoDifferentRequestsCannotOverfillOneSlot() = runBlocking {
        val draft = setup(quantity = 1)
        val outcomes = (1..2).map { async(Dispatchers.Default) { runCatching { staff().complete(draft.copy(id = UUID.randomUUID().toString())) }.isSuccess } }.awaitAll()
        assertEquals(1, outcomes.count { it }); assertEquals(1, boxes.load().batches.single().packed)
    }
    @Test fun menuPriceChangeAndRevokedPermissionRejectOldDraft() = runBlocking {
        val draft = setup()
        menu.save(menu.load().items.single().toDraft().copy(price = "35000"))
        assertTrue(runCatching { staff().complete(draft) }.isFailure)
        val member = employees.load().single()
        employees.changeAccess(member.id, member.revision, EmployeeStatus.REVOKED, "Nghỉ việc")
        assertTrue(runCatching { staff().complete(draft) }.isFailure)
        assertTrue(staff().load().boxes.isEmpty())
        assertEquals(0, boxes.load().batches.single().packed)
    }
    @Test fun unknownEmployeeCannotReadOrPackAnotherStore() = runBlocking {
        val draft = setup()
        val outsider = LocalStaffPackingRepository(context, "outsider@example.com", directory, clock = { now })
        assertTrue(outsider.load().batches.isEmpty())
        assertTrue(runCatching { outsider.complete(draft) }.isFailure)
    }
    @Test fun rejectedBoxAllowsOneReplacementAndOwnerKeepsCorrectSaleCapacity() = runBlocking {
        val draft = setup(quantity = 1)
        val first = staff().complete(draft)
        LocalStaffBoxConditionRepository(context, "an@example.com", directory) { now }.inspect(
            BoxInspectionDraft("reject", first.id, 0, InspectionResult.REJECTED, InspectionReason.PACKAGING))
        val before = boxes.load().batches.single()
        assertEquals(1, before.rejected); assertEquals(0, before.eligible); assertEquals(0, before.prepared)
        val results = (1..2).map { async(Dispatchers.Default) { runCatching { staff().complete(draft.copy(id = UUID.randomUUID().toString())) }.isSuccess } }.awaitAll()
        assertEquals(1, results.count { it })
        val batch = boxes.load().batches.single()
        assertEquals(2, batch.packed); assertEquals(1, batch.rejected); assertEquals(1, batch.eligible); assertEquals(1, batch.prepared)
        assertEquals(1, batch.quantity); assertEquals(1, batch.remaining); assertEquals(0, batch.sold)
        assertEquals(1, boxes.saveBatch(batch.toDraft()).batches.single().quantity)
    }
}
