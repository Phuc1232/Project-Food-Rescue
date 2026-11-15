package com.foodrescue.app.feature.store

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.foodrescue.app.core.common.GeoPoint
import com.foodrescue.app.feature.store.data.LocalOwnerStoreRepository
import com.foodrescue.app.feature.store.data.LocalPublicStoreRepository
import com.foodrescue.app.feature.store.domain.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class StoreLocationPersistenceTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "map_qa_${UUID.randomUUID()}_"
    private val names = mutableSetOf<String>()
    private val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
            names += prefix + name
            return base.getSharedPreferences(prefix + name, mode)
        }
    }
    @After fun cleanup() { names.forEach { base.deleteSharedPreferences(it) } }

    @Test fun approvedPinRoundTripsAndPublicDirectoryUsesLatestCoordinates() = runBlocking {
        val owner = LocalOwnerStoreRepository(context, "owner@foodrescue.vn")
        val profile = owner.load().profile.copy(location = GeoPoint(10.777, 106.699))
        owner.updateProfile(profile)
        assertEquals(profile, LocalOwnerStoreRepository(context, " OWNER@FOODRESCUE.VN ").load().profile)
        val first = LocalPublicStoreRepository(context).getStores().single()
        assertTrue(first.id < 0)
        assertEquals(profile.pickupLocation, first.location)
        val changed = profile.copy(address = "49 Nguyễn Thượng Hiền", location = GeoPoint(10.778, 106.700))
        owner.updateProfile(changed)
        val second = LocalPublicStoreRepository(context).getStores().single()
        assertEquals(first.id, second.id)
        assertEquals(changed.pickupLocation, second.location)
        assertEquals(changed.name, second.name)
    }

    @Test fun draftsPendingAndLegacyProfilesNeverBecomePublicPins() = runBlocking {
        val owner = LocalOwnerStoreRepository(context, "owner@foodrescue.vn")
        val profile = owner.load().profile.copy(location = GeoPoint(10.777, 106.699))
        val partner = LocalOwnerStoreRepository(context, "partner@example.com")
        partner.saveDraft(profile)
        assertTrue(LocalPublicStoreRepository(context).getStores().isEmpty())
        partner.submit(profile)
        assertEquals(StoreReviewStatus.PENDING, partner.load().status)
        assertTrue(LocalPublicStoreRepository(context).getStores().isEmpty())
        // Persist a version-1 approved record without the new optional field.
        owner.updatePayout(StorePayoutAccount("ACB", "NGUYEN AN", "0123456789"))
        assertNull(LocalOwnerStoreRepository(context, "owner@foodrescue.vn").load().profile.location)
        assertTrue(LocalPublicStoreRepository(context).getStores().isEmpty())
        owner.updateProfile(profile)
        assertEquals(1, LocalPublicStoreRepository(context).getStores().size)
        assertEquals(StoreReviewStatus.PENDING, partner.load().status)
    }

    @Test fun businessEmailAndBankCodePersistWithoutPublishingLoginIdentity() = runBlocking {
        val owner = LocalOwnerStoreRepository(context, "owner@foodrescue.vn")
        val profile = owner.load().profile.copy(location = GeoPoint(10.777, 106.699), contactEmail = "store@example.com")
        owner.updateProfile(profile)
        owner.updatePayout(StorePayoutAccount("MB", "CONG TY BANH 365", "00123456789"))
        val restored = LocalOwnerStoreRepository(context, "owner@foodrescue.vn").load()
        assertEquals("MBBank", restored.payout?.bank)
        assertEquals("00123456789", restored.payout?.number)
        assertEquals("store@example.com", restored.profile.contactEmail)
        val public = LocalPublicStoreRepository(context).getStores().single()
        assertEquals("store@example.com", public.contactEmail)
        assertFalse(public.toString().contains("owner@foodrescue.vn"))
        assertFalse(public.toString().contains("00123456789"))
    }

    @Test fun temporaryApprovalRequiresValidSubmissionAndPersistsWhenPolicyIsDisabled() = runBlocking {
        val email = "new-partner@example.com"
        val partner = LocalOwnerStoreRepository(context, email, autoApproveSubmittedApplications = true)
        val profile = LocalOwnerStoreRepository(context, "owner@foodrescue.vn").load().profile
            .copy(location = GeoPoint(10.777, 106.699))
        partner.saveDraft(profile)
        assertEquals(StoreReviewStatus.DRAFT, partner.load().status)
        try { partner.submit(profile.copy(location = null)); fail("Approved an incomplete application") }
        catch (_: IllegalArgumentException) {}
        assertEquals(StoreReviewStatus.DRAFT, partner.load().status)
        val approved = partner.submit(profile)
        assertEquals(StoreReviewStatus.APPROVED, approved.status)
        assertNotNull(approved.submittedAtMillis)
        assertNull(approved.payout)
        assertEquals(approved, LocalOwnerStoreRepository(context, email).load())
        assertEquals(profile.name, LocalPublicStoreRepository(context).getStores().single().name)
    }
}
