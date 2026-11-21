package com.foodrescue.app.feature.store

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.foodrescue.app.core.common.GeoPoint
import com.foodrescue.app.feature.store.data.*
import com.foodrescue.app.feature.store.domain.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.After
import org.junit.Test
import java.util.UUID

class StoreApplicationPersistenceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val email = "review.${UUID.randomUUID()}@example.com"
    private val key = LocalStoreRecords.accountKey(email)
    private val profile = OwnerStoreProfile("Bánh Mộc", "Tiệm bánh", "Nguyễn An", "0901234567", "47 Nguyễn Thượng Hiền",
        "Bàn Cờ", location = GeoPoint(10.777, 106.699))
    private val owner get() = LocalOwnerStoreRepository(context, email)
    private val admin get() = LocalStoreApplicationRepository(context, "admin@foodrescue.vn")

    @After fun cleanup() {
        context.getSharedPreferences("owner_store_$key", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("owner_store_directory.v1", Context.MODE_PRIVATE).edit().remove("store.$key").commit()
    }
    @Test fun ownerSubmitAdminAmendOwnerResubmitAdminApproveAndMapPublicationUseSameRecord() = runBlocking {
        owner.submit(profile)
        val pending = admin.applications().single { it.accountKey == key }
        assertFalse(LocalPublicStoreRepository(context).getStores().any { it.id == pending.storeId })
        admin.review(key, pending.store.revision, StoreReviewAction.CHANGES_REQUESTED, "Bổ sung địa chỉ chính xác")
        val returned = owner.load()
        assertEquals(StoreReviewStatus.CHANGES_REQUIRED, returned.status)
        assertEquals("Bổ sung địa chỉ chính xác", returned.reviewNote)
        val next = owner.submit(profile.copy(address = "49 Nguyễn Thượng Hiền"))
        admin.review(key, next.revision, StoreReviewAction.APPROVED, "Thông tin đã được đối chiếu")
        val approved = owner.load()
        assertEquals(StoreReviewStatus.APPROVED, approved.status)
        assertEquals(4, approved.reviewHistory.size)
        assertEquals(approved, LocalOwnerStoreRepository(context, email.uppercase()).load())
        assertEquals("49 Nguyễn Thượng Hiền, Bàn Cờ, TP. Hồ Chí Minh",
            LocalPublicStoreRepository(context).getStores().single { it.id == pending.storeId }.location.address)
    }
    @Test fun simultaneousReviewersProduceExactlyOneDecision() = runBlocking {
        val pending = owner.submit(profile)
        val outcomes = List(2) { async(Dispatchers.IO) { runCatching {
            admin.review(key, pending.revision, StoreReviewAction.APPROVED, "")
        } } }.awaitAll()
        assertEquals(1, outcomes.count { it.isSuccess })
        assertTrue(outcomes.single { it.isFailure }.exceptionOrNull() is StaleStoreApplication)
        assertEquals(2, owner.load().reviewHistory.size)
    }
    @Test fun unsubmittedDraftIsNotExposedToReviewQueueAndNonAdminCannotReadOrWrite() = runBlocking {
        owner.saveDraft(profile)
        assertFalse(admin.applications().any { it.accountKey == key })
        val pending = owner.submit(profile)
        val denied = LocalStoreApplicationRepository(context, "not-admin@foodrescue.vn")
        assertTrue(runCatching { denied.applications() }.isFailure)
        assertTrue(runCatching { denied.review(key, pending.revision, StoreReviewAction.APPROVED, "") }.isFailure)
        assertEquals(StoreReviewStatus.PENDING, owner.load().status)
    }
    @Test fun legacyApprovedRecordRemainsApprovedAndBankLeadingZerosSurviveUpdate() = runBlocking {
        val bank = StorePayoutAccount("Vietcombank", "NGUYEN AN", "0012345678")
        val old = OwnerStore(profile, StoreReviewStatus.APPROVED, 123L, "Đã kiểm tra", bank)
        val json = JSONObject(OwnerStoreJson.encode(old)).apply { remove("revision"); remove("reviewHistory") }
        val beforeStarter = LocalOwnerStoreRepository(context, "owner@foodrescue.vn").load()
        context.getSharedPreferences("owner_store_$key", Context.MODE_PRIVATE).edit().putString("store.v1", json.toString()).commit()
        LocalPublicStoreRepository(context).register(key)
        assertEquals(old, owner.load())
        owner.updateProfile(profile.copy(description = "Bánh tươi mỗi ngày"))
        assertEquals(bank, owner.load().payout)
        assertEquals(StoreReviewStatus.APPROVED, owner.load().status)
        assertEquals(beforeStarter, LocalOwnerStoreRepository(context, "owner@foodrescue.vn").load())
    }
    @Test fun staleDecisionAfterResubmissionLeavesCurrentApplicationUntouched() = runBlocking {
        val old = owner.submit(profile)
        admin.review(key, old.revision, StoreReviewAction.CHANGES_REQUESTED, "Bổ sung địa chỉ chính xác")
        val current = owner.submit(profile)
        assertTrue(runCatching { admin.review(key, old.revision, StoreReviewAction.APPROVED, "") }.exceptionOrNull() is StaleStoreApplication)
        assertEquals(current, owner.load())
    }
}
