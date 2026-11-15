package com.foodrescue.app.feature.order.data

import android.annotation.SuppressLint
import android.content.Context
import com.foodrescue.app.core.datastore.LocalCommerceLock
import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.surprisebox.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

internal class LocalOrderLedger(context: Context) : BoxStockSource {
    private val preferences = context.applicationContext.getSharedPreferences("commerce_orders.v2", Context.MODE_PRIVATE)
    internal data class Row(val account: String, val order: BoxReservation)
    internal fun read(): List<Row> {
        val rows = JSONArray(preferences.getString("orders", "[]"))
        return (0 until rows.length()).map { rows.getJSONObject(it).let { row -> Row(row.getString("account"), OrderJson.read(row.getJSONObject("order"))) } }
    }
    @SuppressLint("UseKtx")
    internal fun write(rows: List<Row>, migrated: String? = null) {
        val json = JSONArray().apply { rows.forEach { put(JSONObject().apply { put("account", it.account); put("order", OrderJson.write(it.order)) }) } }
        val editor = preferences.edit().putString("orders", json.toString())
        if (migrated != null) editor.putBoolean("migrated.$migrated", true)
        check(editor.commit()) { "Order write failed" }
    }
    internal fun migrate(account: String, legacy: OrderHistoryStore) = synchronized(LocalCommerceLock) {
        if (!preferences.getBoolean("migrated.$account", false)) {
            val current = read()
            val existing = current.filter { it.account == account }.map { it.order.id }.toSet()
            write(current + legacy.load().filter { it.id !in existing }.map { Row(account, it) }, migrated = account)
        }
    }
    override fun stock(storeId: Int, batchId: String, now: Long): BoxStock = synchronized(LocalCommerceLock) {
        val orders = read().map { it.order }.filter { it.boxSnapshot?.storeId == storeId && it.boxSnapshot.batchId == batchId && it.occupiesStock(now) }
        BoxStock(orders.filter { !it.isPaid }.sumOf { it.quantity }, orders.filter { it.isPaid }.sumOf { it.quantity })
    }
    internal companion object {
        fun accountKey(email: String): String = MessageDigest.getInstance("SHA-256")
            .digest(email.trim().lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
