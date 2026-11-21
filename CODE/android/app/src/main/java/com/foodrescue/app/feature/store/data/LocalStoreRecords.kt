package com.foodrescue.app.feature.store.data

import android.annotation.SuppressLint
import android.content.Context
import com.foodrescue.app.core.datastore.LocalCommerceLock
import com.foodrescue.app.feature.store.domain.OwnerStore
import java.security.MessageDigest
import java.util.Locale

/** Both roles share one write lock and one durable record: a decision and its audit commit together. */
internal class LocalStoreRecords(context: Context) {
    private val context = context.applicationContext
    private val directory = LocalPublicStoreRepository(this.context)
    private fun preferences(key: String) = context.getSharedPreferences("owner_store_$key", Context.MODE_PRIVATE)

    fun read(key: String): OwnerStore? = synchronized(LocalCommerceLock) {
        preferences(key).getString("store.v1", null)?.let(OwnerStoreJson::decode)
    }
    fun contains(key: String): Boolean = preferences(key).contains("store.v1")

    @SuppressLint("UseKtx", "ApplySharedPref")
    fun change(key: String, fallback: () -> OwnerStore, transform: (OwnerStore) -> OwnerStore): OwnerStore = synchronized(LocalCommerceLock) {
        val current = read(key) ?: fallback()
        val updated = transform(current).copy(revision = current.revision + 1)
        directory.register(key)
        val prefs = preferences(key)
        val before = prefs.getString("store.v1", null)
        if (!prefs.edit().putString("store.v1", OwnerStoreJson.encode(updated)).commit()) {
            // SharedPreferences publishes to memory before disk; rollback before releasing commerce readers.
            prefs.edit().putString("store.v1", before).commit()
            error("Store write failed")
        }
        updated
    }

    companion object {
        fun accountKey(email: String): String = MessageDigest.getInstance("SHA-256")
            .digest(email.trim().lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
