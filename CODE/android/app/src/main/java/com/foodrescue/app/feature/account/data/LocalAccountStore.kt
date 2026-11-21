package com.foodrescue.app.feature.account.data

import android.annotation.SuppressLint
import android.content.Context
import com.foodrescue.app.core.datastore.LocalCommerceLock
import com.foodrescue.app.feature.account.domain.*
import java.security.MessageDigest
import java.util.Locale

internal fun accountEmail(email: String) = email.trim().lowercase(Locale.ROOT)

/** Account status and its audit have a single durable commit. No passwords or OTPs are stored. */
internal class LocalAccountStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("account_directory.v1", Context.MODE_PRIVATE)
    fun all(): List<AccountRecord> = synchronized(LocalCommerceLock) {
        prefs.getString("records", null)?.let(AccountJson::decode) ?: emptyList()
    }
    fun read(key: String) = synchronized(LocalCommerceLock) { all().find { it.key == key } ?: AccountRecord(key) }
    @SuppressLint("UseKtx", "ApplySharedPref")
    fun change(key: String, transform: (AccountRecord) -> AccountRecord): AccountRecord = synchronized(LocalCommerceLock) {
        val records = all(); val before = records.find { it.key == key } ?: AccountRecord(key)
        val after = transform(before); require(after.key == key)
        if (after == before && records.any { it.key == key }) return@synchronized after
        val raw = prefs.getString("records", null)
        val next = records.filterNot { it.key == key } + after
        if (!prefs.edit().putString("records", AccountJson.encode(next)).commit()) {
            prefs.edit().putString("records", raw).commit()
            error("Account write failed")
        }
        after
    }
    companion object {
        fun key(email: String): String = MessageDigest.getInstance("SHA-256")
            .digest(accountEmail(email).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

