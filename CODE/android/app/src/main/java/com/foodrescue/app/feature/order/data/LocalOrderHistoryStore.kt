package com.foodrescue.app.feature.order.data

import android.content.Context
import androidx.core.content.edit
import com.foodrescue.app.feature.order.domain.BoxReservation
import com.foodrescue.app.feature.order.domain.OrderHistoryStore
import com.foodrescue.app.feature.order.domain.PickupWindow
import com.foodrescue.app.feature.payment.domain.PaymentStatus
import com.foodrescue.app.feature.payment.domain.QrPayment
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

/** Device-local customer history, kept separate from the session and from other accounts. */
internal class LocalOrderHistoryStore(context: Context, email: String) : OrderHistoryStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        "customer_orders_" + MessageDigest.getInstance("SHA-256")
            .digest(email.trim().lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }, Context.MODE_PRIVATE,
    )

    override fun load(): List<BoxReservation> {
        val rows = runCatching { JSONArray(preferences.getString("orders.v1", "[]")) }.getOrNull() ?: return emptyList()
        return (0 until rows.length()).mapNotNull { index -> runCatching {
            val row = rows.getJSONObject(index)
            val payment = row.optJSONObject("payment")?.let {
                QrPayment(it.getString("reference"), it.getLong("amount"),
                    PaymentStatus.valueOf(it.getString("status")), it.getLong("updatedAt"))
            }
            BoxReservation(row.getString("id"), row.getInt("boxId"), row.getInt("quantity"),
                PickupWindow(row.getLong("pickupStart"), row.getLong("pickupEnd")),
                row.getLong("createdAt"), row.getLong("expiresAt"), row.getInt("unitPrice"), payment,
                row.optionalLong("cancelledAt"), row.optionalLong("collectedAt"))
        }.getOrNull() }.distinctBy { it.id }
    }

    override fun save(orders: List<BoxReservation>) {
        val rows = JSONArray()
        orders.forEach { order ->
            rows.put(JSONObject().apply {
                put("id", order.id); put("boxId", order.boxId); put("quantity", order.quantity)
                put("pickupStart", order.pickup.startMillis); put("pickupEnd", order.pickup.endMillis)
                put("createdAt", order.createdAtMillis); put("expiresAt", order.expiresAtMillis)
                put("unitPrice", order.unitPrice)
                put("cancelledAt", order.cancelledAtMillis); put("collectedAt", order.collectedAtMillis)
                order.payment?.let { payment -> put("payment", JSONObject().apply {
                    put("reference", payment.reference); put("amount", payment.amount)
                    put("status", payment.status.name); put("updatedAt", payment.updatedAtMillis)
                }) }
            })
        }
        preferences.edit { putString("orders.v1", rows.toString()) }
    }

    private fun JSONObject.optionalLong(key: String): Long? = if (isNull(key)) null else getLong(key)
}
