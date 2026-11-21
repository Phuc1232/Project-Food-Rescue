package com.foodrescue.app.feature.store.data

import android.annotation.SuppressLint
import android.content.Context
import com.foodrescue.app.core.datastore.LocalCommerceLock
import com.foodrescue.app.core.common.PickupLocation
import com.foodrescue.app.feature.store.domain.PublicStore
import com.foodrescue.app.feature.store.domain.PublicStoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** The index holds only hashed record keys. Public values are read from the saved profile,
 * so a profile/coordinate change cannot leave a stale independently-published copy behind. */
internal class LocalPublicStoreRepository(context: Context) : PublicStoreRepository {
    private val context = context.applicationContext
    private val index = this.context.getSharedPreferences("owner_store_directory.v1", Context.MODE_PRIVATE)

    @SuppressLint("UseKtx") // Publication must not be reported successful if index persistence fails.
    internal fun register(accountHash: String): Int = synchronized(lock) {
        val key = "store." + accountHash
        if (index.contains(key)) return@synchronized index.getInt(key, -10_000)
        val id = index.getInt("nextId", -10_000)
        check(id > Int.MIN_VALUE)
        check(index.edit().putInt(key, id).putInt("nextId", id - 1).commit())
        id
    }

    override suspend fun getStores(): List<PublicStore> = withContext(Dispatchers.IO) { readStores() }

    internal fun registeredAccounts(): Map<String, Int> = synchronized(lock) {
        index.all.mapNotNull { (key, id) ->
            val hash = key.removePrefix("store.")
            if (key.startsWith("store.") && id is Int && hash.matches(Regex("[0-9a-f]{64}"))) hash to id else null
        }.toMap()
    }

    internal fun readStores(): List<PublicStore> = synchronized(LocalCommerceLock) {
        index.all.mapNotNull { (key, value) ->
            if (!key.startsWith("store.") || value !is Int) return@mapNotNull null
            val hash = key.removePrefix("store.")
            if (!hash.matches(Regex("[0-9a-f]{64}"))) return@mapNotNull null
            runCatching {
                val raw = context.getSharedPreferences("owner_store_" + hash, Context.MODE_PRIVATE).getString("store.v1", null)
                    ?: return@runCatching null
                val store = JSONObject(raw)
                if (store.optString("status") != "APPROVED") return@runCatching null
                if (OwnerStoreJson.decode(raw).governance.suspension != null) return@runCatching null
                val profile = store.getJSONObject("profile")
                val pin = profile.optJSONObject("location") ?: return@runCatching null
                val lat = pin.getDouble("latitude")
                if (!lat.isFinite() || lat !in -85.0..85.0) return@runCatching null
                val address = listOf(profile.getString("address"), profile.getString("area"), "TP. Hồ Chí Minh").joinToString(", ")
                PublicStore(value, profile.getString("name"), profile.getString("category"), profile.getString("description"),
                    profile.getString("phone"), profile.getString("area"), profile.getString("open"), profile.getString("close"),
                    PickupLocation(lat, pin.getDouble("longitude"), address), profile.optString("contactEmail"))
            }.getOrNull()
        }.sortedByDescending { it.id }
    }

    private companion object { val lock = Any() }
}
