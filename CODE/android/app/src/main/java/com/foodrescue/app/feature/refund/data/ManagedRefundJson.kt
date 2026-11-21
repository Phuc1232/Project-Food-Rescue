package com.foodrescue.app.feature.refund.data

import com.foodrescue.app.feature.refund.domain.*
import org.json.JSONArray
import org.json.JSONObject

internal object ManagedRefundJson {
    fun read(rows: JSONArray?): List<ManagedRefund> = if (rows == null) emptyList() else
        (0 until rows.length()).map { index -> rows.getJSONObject(index).let { row ->
            val events = row.getJSONArray("history")
            ManagedRefund(row.getString("id"), RefundSource.valueOf(row.getString("source")), row.getString("sourceId"),
                row.getString("reason"), row.getLong("amount"), row.getLong("requestedAt"),
                AdminRefundStatus.valueOf(row.getString("status")), (0 until events.length()).map { i ->
                    events.getJSONObject(i).let { RefundEvent(AdminRefundStatus.valueOf(it.getString("status")),
                        it.getString("actor"), it.getLong("at"), it.getString("note")) }
                })
        } }
    fun write(rows: List<ManagedRefund>) = JSONArray().apply { rows.forEach { refund -> put(JSONObject().apply {
        put("id", refund.id); put("source", refund.source.name); put("sourceId", refund.sourceId)
        put("reason", refund.reason); put("amount", refund.amount); put("requestedAt", refund.requestedAtMillis)
        put("status", refund.status.name); put("history", JSONArray().apply { refund.history.forEach { event ->
            put(JSONObject().apply { put("status", event.status.name); put("actor", event.actor); put("at", event.atMillis); put("note", event.note) })
        } })
    }) } }
}
