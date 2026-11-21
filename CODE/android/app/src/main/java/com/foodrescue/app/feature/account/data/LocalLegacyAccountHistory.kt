package com.foodrescue.app.feature.account.data

import android.content.Context
import com.foodrescue.app.feature.order.data.LocalOrderHistoryStore
import com.foodrescue.app.feature.order.data.LocalOrderLedger
import java.io.File

/** Existing nonempty histories identify an account by its original hash, never by a guessed email. */
internal class LocalLegacyAccountHistory(private val context: Context,
    private val accountKeys: () -> List<String> = {
        File(context.applicationInfo.dataDir, "shared_prefs").listFiles().orEmpty().mapNotNull {
            Regex("customer_orders_([0-9a-f]{64})\\.xml").matchEntire(it.name)?.groupValues?.get(1)
        }
    }) {
    fun rows(): List<LocalOrderLedger.Row> = accountKeys().distinct().flatMap { key ->
        LocalOrderHistoryStore.forAccountKey(context, key).load().map { LocalOrderLedger.Row(key, it) }
    }
}
