package com.foodrescue.app.feature.store.data

import android.content.Context
import com.foodrescue.app.feature.store.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** Account-scoped frontend persistence with an opt-in temporary approval policy. */
internal class LocalOwnerStoreRepository(
    context: Context,
    email: String,
    private val autoApproveSubmittedApplications: Boolean = false,
) : OwnerStoreRepository {
    private val account = email.trim().lowercase(Locale.ROOT)
    private val accountHash = LocalStoreRecords.accountKey(account)
    private val records = LocalStoreRecords(context)

    fun hasApplication(): Boolean = account == STARTER_ACCOUNT || records.contains(accountHash)

    override suspend fun load(): OwnerStore = withContext(Dispatchers.IO) { records.read(accountHash) ?: initialStore() }
    override suspend fun saveDraft(profile: OwnerStoreProfile): OwnerStore = change {
        check(status == StoreReviewStatus.DRAFT || status == StoreReviewStatus.CHANGES_REQUIRED)
        copy(profile = profile)
    }
    override suspend fun submit(profile: OwnerStoreProfile): OwnerStore = change {
        val submitted = submit(profile, System.currentTimeMillis())
        if (autoApproveSubmittedApplications) submitted.copy(status = StoreReviewStatus.APPROVED) else submitted
    }
    override suspend fun updateProfile(profile: OwnerStoreProfile): OwnerStore = change { updateProfile(profile) }
    override suspend fun updatePayout(payout: StorePayoutAccount): OwnerStore = change { updatePayout(payout, LocalPayoutBanks.all) }

    private suspend fun change(transform: OwnerStore.() -> OwnerStore): OwnerStore = withContext(Dispatchers.IO) {
        records.change(accountHash, ::initialStore, transform)
    }
    private fun initialStore() = if (account == STARTER_ACCOUNT) starterStore() else OwnerStore()

    private fun starterStore() = OwnerStore(
        profile = OwnerStoreProfile("Tiệm Bánh An Nhiên", "Tiệm bánh", "Nguyễn An",
            "0901234567", "47 Nguyễn Thượng Hiền", "Bàn Cờ",
            "Bánh tươi mỗi ngày. Cùng FoodRescue trao những phần bánh ngon đến người yêu ẩm thực.", "07:00", "21:00"),
        status = StoreReviewStatus.APPROVED,
        payout = StorePayoutAccount("Vietcombank", "NGUYEN AN", "0123456789"),
    )

    private companion object {
        const val STARTER_ACCOUNT = "owner@foodrescue.vn"
    }
}
