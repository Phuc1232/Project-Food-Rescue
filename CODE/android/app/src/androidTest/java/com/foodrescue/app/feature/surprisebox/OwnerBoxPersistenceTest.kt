package com.foodrescue.app.feature.surprisebox

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import com.foodrescue.app.feature.store.domain.*
import com.foodrescue.app.feature.surprisebox.data.LocalOwnerBoxRepository
import com.foodrescue.app.feature.surprisebox.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class OwnerBoxPersistenceTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "boxes_qa_${UUID.randomUUID()}_"
    private val preferences = mutableSetOf<String>()
    private val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            preferences += prefix + name
            return base.getSharedPreferences(prefix + name, mode)
        }
    }
    private val owner = object : OwnerStoreRepository {
        var status = StoreReviewStatus.APPROVED
        override suspend fun load() = OwnerStore(status = status)
        override suspend fun saveDraft(profile: OwnerStoreProfile): OwnerStore = error("Unused")
        override suspend fun submit(profile: OwnerStoreProfile): OwnerStore = error("Unused")
        override suspend fun updateProfile(profile: OwnerStoreProfile): OwnerStore = error("Unused")
        override suspend fun updatePayout(payout: StorePayoutAccount): OwnerStore = error("Unused")
    }
    private fun repo(email: String = "owner@example.com") = LocalOwnerBoxRepository(context, email, owner) { 1500L }
    private val draft = BoxTemplateDraft(name = "Hộp bánh cuối ngày", description = "Bánh tươi mỗi ngày", allergens = "Sữa, trứng", minimumValue = "120000", price = "49000")
    @After fun cleanup() { preferences.forEach { base.deleteSharedPreferences(it) } }

    @Test fun restartAndAccountIsolationPreserveCommercialSnapshot() = runBlocking {
        val repository = repo()
        val template = repository.saveTemplate(draft).templates.single()
        val batch = repository.saveBatch(SaleBatchDraft(templateId = template.id, quantity = "10", salesStart = 1000,
            salesEnd = 3000, pickupStart = 4000, pickupEnd = 5000)).batches.single()
        repository.operate(batch.id, BatchAction.PUBLISH)
        repository.saveTemplate(template.toDraft().copy(price = "59000"))
        val restored = repo(" OWNER@EXAMPLE.COM ").load()
        assertEquals(59000L, restored.templates.single().price)
        assertEquals(49000L, restored.batches.single().template.price)
        assertEquals(BatchControl.PUBLISHED, restored.batches.single().control)
        assertTrue(repo("another@example.com").load().batches.isEmpty())
    }
    @Test fun unapprovedStoreCannotChangeCatalog() = runBlocking {
        val repository = repo()
        repository.saveTemplate(draft)
        owner.status = StoreReviewStatus.PENDING
        try { repository.saveTemplate(draft.copy(name = "Hộp mới")); fail("Expected denied write") } catch (_: IllegalStateException) { }
        assertEquals(1, repository.load().templates.size)
    }
    @Test fun invalidEditLeavesPersistedBatchIntact() = runBlocking {
        val repository = repo()
        val template = repository.saveTemplate(draft).templates.single()
        val batch = repository.saveBatch(SaleBatchDraft(templateId = template.id, quantity = "10", salesStart = 1000,
            salesEnd = 3000, pickupStart = 4000, pickupEnd = 5000)).batches.single()
        try { repository.saveBatch(batch.toDraft().copy(quantity = "0")); fail("Expected invalid quantity") } catch (_: BoxValidationException) { }
        assertEquals(10, repo().load().batches.single().quantity)
    }
}
