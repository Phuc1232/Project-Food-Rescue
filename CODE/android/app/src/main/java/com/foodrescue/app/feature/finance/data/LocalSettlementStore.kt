package com.foodrescue.app.feature.finance.data

import android.annotation.SuppressLint
import android.content.Context
import com.foodrescue.app.core.datastore.LocalCommerceLock
import com.foodrescue.app.feature.finance.domain.RevenueSettlement

/** Owner reads and administrator decisions share the same durable records and commerce lock. */
internal class LocalSettlementStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("owner_settlements.v1", Context.MODE_PRIVATE)
    fun read(storeId: Int): List<RevenueSettlement> = synchronized(LocalCommerceLock) {
        preferences.getString("store.$storeId", null)?.let(SettlementJson::decode) ?: emptyList()
    }
    @SuppressLint("UseKtx", "ApplySharedPref")
    fun write(storeId: Int, records: List<RevenueSettlement>) = synchronized(LocalCommerceLock) {
        require(records.all { it.storeId == storeId } && records.distinctBy { it.id }.size == records.size)
        val key = "store.$storeId"; val before = preferences.getString(key, null)
        if (!preferences.edit().putString(key, SettlementJson.encode(records)).commit()) {
            preferences.edit().putString(key, before).commit(); error("Settlement write failed")
        }
    }
}
