package com.foodrescue.app.feature.store

import androidx.test.platform.app.InstrumentationRegistry
import com.foodrescue.app.feature.store.data.LocalOwnerStoreRepository
import com.foodrescue.app.feature.store.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class LocalOwnerStoreRepositoryTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val profile = OwnerStoreProfile("Cửa hàng kiểm tra", "Tiệm bánh", "Nguyễn An",
        "0901234567", "47 Nguyễn Thượng Hiền", "Bàn Cờ", location = com.foodrescue.app.core.common.GeoPoint(10.777, 106.699))

    @Test fun draftAndPendingPersistAcrossInstancesAndEmailCase() = runBlocking {
        val email = "owner.qa." + UUID.randomUUID() + "@example.com"
        val repository = LocalOwnerStoreRepository(context, email)
        assertFalse(repository.hasApplication())
        repository.saveDraft(profile.copy(name = ""))
        assertTrue(repository.hasApplication())
        assertEquals("", LocalOwnerStoreRepository(context, email.uppercase()).load().profile.name)
        val submitted = repository.submit(profile)
        val restored = LocalOwnerStoreRepository(context, "  " + email.uppercase() + "  ").load()
        assertEquals(submitted, restored)
        assertEquals(StoreReviewStatus.PENDING, restored.status)
        assertTrue(restored.submittedAtMillis != null)
        assertEquals(StoreReviewStatus.DRAFT, LocalOwnerStoreRepository(context, UUID.randomUUID().toString() + "@example.com").load().status)
        try { repository.updatePayout(StorePayoutAccount("ACB", "NGUYEN AN", "0123456789")); fail("Pending account changed bank") }
        catch (_: IllegalStateException) {}
        assertEquals(submitted, repository.load())
    }

    @Test fun emailSubstringDoesNotGrantApprovedStore() = runBlocking {
        val unrelated = LocalOwnerStoreRepository(context, "not-owner@foodrescue.vn")
        assertFalse(unrelated.hasApplication())
        assertEquals(StoreReviewStatus.DRAFT, unrelated.load().status)
        assertEquals(StoreReviewStatus.APPROVED, LocalOwnerStoreRepository(context, " OWNER@FOODRESCUE.VN ").load().status)
    }
}
