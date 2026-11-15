package com.foodrescue.app.feature.store.data

import android.content.Context
import android.annotation.SuppressLint
import com.foodrescue.app.core.common.GeoPoint
import com.foodrescue.app.feature.store.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

/** Account-scoped frontend persistence with an opt-in temporary approval policy. */
internal class LocalOwnerStoreRepository(
    context: Context,
    email: String,
    private val autoApproveSubmittedApplications: Boolean = false,
) : OwnerStoreRepository {
    private val account = email.trim().lowercase(Locale.ROOT)
    private val accountHash = MessageDigest.getInstance("SHA-256").digest(account.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    private val publicStores = LocalPublicStoreRepository(context)
    private val preferences = context.applicationContext.getSharedPreferences(
        "owner_store_" + accountHash, Context.MODE_PRIVATE)

    fun hasApplication(): Boolean = account == STARTER_ACCOUNT || preferences.contains(KEY)

    override suspend fun load(): OwnerStore = withContext(Dispatchers.IO) { read() }
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

    // KTX edit discards commit's result; failed persistence must not be reported as saved.
    @SuppressLint("UseKtx")
    private suspend fun change(transform: OwnerStore.() -> OwnerStore): OwnerStore = withContext(Dispatchers.IO) {
        val updated = read().transform()
        val profile = updated.profile
        val json = JSONObject().apply {
            put("version", 1); put("status", updated.status.name)
            put("submittedAt", updated.submittedAtMillis); put("reviewNote", updated.reviewNote)
            put("profile", JSONObject().apply {
                put("name", profile.name); put("category", profile.category); put("contact", profile.contactName)
                put("phone", profile.phone); put("address", profile.address); put("area", profile.area)
                put("description", profile.description); put("open", profile.opensAt); put("close", profile.closesAt)
                put("contactEmail", profile.contactEmail)
                profile.location?.let { put("location", JSONObject().apply {
                    put("latitude", it.latitude); put("longitude", it.longitude)
                }) }
            })
            updated.payout?.let { put("payout", JSONObject().apply {
                put("bank", it.bank); put("holder", it.holder); put("number", it.number)
                put("bankCode", LocalPayoutBanks.all.findBank(it.bank)?.code)
            }) }
        }
        publicStores.register(accountHash)
        check(preferences.edit().putString(KEY, json.toString()).commit()) { "Store write failed" }
        updated
    }

    private fun read(): OwnerStore {
        val raw = preferences.getString(KEY, null) ?: return if (account == STARTER_ACCOUNT) starterStore() else OwnerStore()
        val json = JSONObject(raw)
        require(json.getInt("version") == 1)
        val profile = json.getJSONObject("profile")
        return OwnerStore(
            OwnerStoreProfile(profile.getString("name"), profile.getString("category"), profile.getString("contact"),
                profile.getString("phone"), profile.getString("address"), profile.getString("area"),
                profile.getString("description"), profile.getString("open"), profile.getString("close"),
                profile.optJSONObject("location")?.let { GeoPoint(it.getDouble("latitude"), it.getDouble("longitude")) }, profile.optString("contactEmail")),
            StoreReviewStatus.valueOf(json.getString("status")),
            if (json.isNull("submittedAt")) null else json.getLong("submittedAt"), json.optString("reviewNote"),
            json.optJSONObject("payout")?.let { StorePayoutAccount(LocalPayoutBanks.all.findBank(it.optString("bankCode"))?.name
                ?: it.getString("bank"), it.getString("holder"), it.getString("number")) })
    }

    private fun starterStore() = OwnerStore(
        profile = OwnerStoreProfile("Tiệm Bánh An Nhiên", "Tiệm bánh", "Nguyễn An",
            "0901234567", "47 Nguyễn Thượng Hiền", "Bàn Cờ",
            "Bánh tươi mỗi ngày. Cùng FoodRescue trao những phần bánh ngon đến người yêu ẩm thực.", "07:00", "21:00"),
        status = StoreReviewStatus.APPROVED,
        payout = StorePayoutAccount("Vietcombank", "NGUYEN AN", "0123456789"),
    )

    private companion object {
        const val STARTER_ACCOUNT = "owner@foodrescue.vn"
        const val KEY = "store.v1"
    }
}
