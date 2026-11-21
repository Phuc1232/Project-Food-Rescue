package com.foodrescue.app.feature.complaint.data

import com.foodrescue.app.feature.complaint.domain.*
import com.foodrescue.app.feature.order.domain.OrderStatus
import org.json.JSONArray
import org.json.JSONObject

/** Additive fields keep the existing cases.v1 records and private evidence identifiers intact. */
internal object ComplaintJson {
    fun encode(items: List<CustomerComplaint>): String = JSONArray().apply { items.forEach { item -> put(JSONObject().apply {
        put("id", item.id); put("reason", item.reason.name); put("description", item.description)
        put("status", item.status.name); put("createdAt", item.createdAtMillis); put("updatedAt", item.updatedAtMillis)
        put("response", item.response); put("evidence", encodeEvidence(item.evidence)); put("revision", item.revision)
        put("order", JSONObject().apply {
            put("id", item.order.id); put("reference", item.order.reference); put("store", item.order.store)
            put("boxName", item.order.boxName); put("quantity", item.order.quantity); put("amount", item.order.amount)
            put("createdAt", item.order.createdAtMillis)
        })
        put("history", JSONArray().apply { item.history.forEach { event -> put(JSONObject().apply {
            put("action", event.action.name); put("actor", event.actor); put("at", event.atMillis); put("note", event.note)
            put("refundAmount", event.refundAmount); put("orderStatus", event.orderStatus?.name); put("paidAmount", event.paidAmount)
        }) } })
        item.refundRequest?.let { request -> put("refundRequest", JSONObject().apply {
            put("id", request.id); put("amount", request.amount); put("requestedAt", request.requestedAtMillis)
        }) }
    }) } }.toString()

    fun decode(raw: String, evidence: LocalEvidenceStore): List<CustomerComplaint> {
        val rows = JSONArray(raw)
        return List(rows.length()) { index ->
            val row = rows.getJSONObject(index)
            val order = row.getJSONObject("order")
            val history = row.optJSONArray("history") ?: JSONArray()
            CustomerComplaint(row.getString("id"), ComplaintOrder(order.getString("id"), order.getString("reference"),
                order.getString("store"), order.getString("boxName"), order.getInt("quantity"), order.getLong("amount"), order.getLong("createdAt")),
                ComplaintReason.valueOf(row.getString("reason")), row.getString("description"), decodeEvidence(row.optJSONArray("evidence"), evidence),
                ComplaintStatus.valueOf(row.getString("status")), row.getLong("createdAt"), row.getLong("updatedAt"),
                row.optString("response").takeIf(String::isNotBlank), row.optLong("revision", 0),
                List(history.length()) { history.getJSONObject(it).let { event ->
                    ComplaintReviewEvent(ComplaintReviewAction.valueOf(event.getString("action")), event.getString("actor"),
                        event.getLong("at"), event.optString("note"), event.optLong("refundAmount", 0),
                        event.optString("orderStatus").takeIf(String::isNotBlank)?.let(OrderStatus::valueOf), event.optLong("paidAmount", 0))
                } }, row.optJSONObject("refundRequest")?.let { ComplaintRefundRequest(it.getString("id"), it.getLong("amount"), it.getLong("requestedAt")) })
        }.also { require(it.distinctBy { row -> row.id }.size == it.size) }.sortedByDescending { it.createdAtMillis }
    }

    fun encodeEvidence(items: List<ComplaintEvidence>): JSONArray = JSONArray().apply { items.forEach { item -> put(JSONObject().apply {
        put("id", item.id); put("name", item.name); put("mime", item.mimeType); put("size", item.sizeBytes)
    }) } }
    fun decodeEvidence(rows: JSONArray?, evidence: LocalEvidenceStore): List<ComplaintEvidence> =
        if (rows == null) emptyList() else List(minOf(rows.length(), MAX_COMPLAINT_IMAGES)) { index ->
            rows.getJSONObject(index).let { evidence.restore(it.getString("id"), it.getString("name"), it.getString("mime"), it.getLong("size")) }
        }.distinctBy { it.id }
}
