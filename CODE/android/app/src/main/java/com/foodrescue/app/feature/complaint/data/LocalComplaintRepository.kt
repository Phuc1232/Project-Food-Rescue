package com.foodrescue.app.feature.complaint.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.edit
import com.foodrescue.app.feature.complaint.domain.*
import org.json.JSONArray
import org.json.JSONObject

/** Frontend history and drafts. New requests stay pending until a response is supplied. */
internal class LocalComplaintRepository(context: Context, email: String, private val evidence: LocalEvidenceStore,
    private val online: (() -> Boolean)? = null) : ComplaintRepository {
    private val preferences = context.applicationContext.getSharedPreferences("customer_complaints_" + complaintAccountKey(email), Context.MODE_PRIVATE)
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    override fun load(): List<CustomerComplaint> {
        val rows = runCatching { JSONArray(preferences.getString("cases.v1", "[]")) }.getOrNull() ?: return emptyList()
        return (0 until rows.length()).mapNotNull { index -> runCatching {
            val row = rows.getJSONObject(index)
            val order = row.getJSONObject("order")
            CustomerComplaint(row.getString("id"), ComplaintOrder(order.getString("id"), order.getString("reference"),
                order.getString("store"), order.getString("boxName"), order.getInt("quantity"), order.getLong("amount"), order.getLong("createdAt")),
                ComplaintReason.valueOf(row.getString("reason")), row.getString("description"), decodeEvidence(row.optJSONArray("evidence")),
                ComplaintStatus.valueOf(row.getString("status")), row.getLong("createdAt"), row.getLong("updatedAt"),
                row.optString("response").takeIf(String::isNotBlank))
        }.getOrNull() }.distinctBy { it.id }.sortedByDescending { it.createdAtMillis }
    }

    override fun loadDraft(): ComplaintDraft? = runCatching {
        val text = preferences.getString("draft.v1", null) ?: return null
        val row = JSONObject(text)
        ComplaintDraft(row.getString("id"), row.optString("orderId").takeIf(String::isNotBlank),
            row.optString("reason").takeIf(String::isNotBlank)?.let(ComplaintReason::valueOf),
            row.optString("description").take(MAX_COMPLAINT_LENGTH), decodeEvidence(row.optJSONArray("evidence")))
    }.getOrNull()

    override fun saveDraft(draft: ComplaintDraft?) {
        preferences.edit {
            if (draft == null) remove("draft.v1")
            else putString("draft.v1", JSONObject().apply {
                put("id", draft.id); put("orderId", draft.orderId); put("reason", draft.reason?.name)
                put("description", draft.description); put("evidence", encodeEvidence(draft.evidence))
            }.toString())
        }
    }

    override fun submit(complaint: CustomerComplaint): Boolean {
        val current = load()
        if (current.any { it.id == complaint.id }) return true
        if (current.any { it.order.id == complaint.order.id && it.status.isOpen }) return false
        val available = online?.invoke() ?: (connectivity?.getNetworkCapabilities(connectivity.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true)
        if (!available) return false
        val rows = JSONArray()
        (listOf(complaint) + current).forEach { item -> rows.put(JSONObject().apply {
            put("id", item.id); put("reason", item.reason.name); put("description", item.description)
            put("status", item.status.name); put("createdAt", item.createdAtMillis); put("updatedAt", item.updatedAtMillis)
            put("response", item.response); put("evidence", encodeEvidence(item.evidence))
            put("order", JSONObject().apply {
                put("id", item.order.id); put("reference", item.order.reference); put("store", item.order.store)
                put("boxName", item.order.boxName); put("quantity", item.order.quantity); put("amount", item.order.amount)
                put("createdAt", item.order.createdAtMillis)
            })
        }) }
        // History and draft removal are one local transaction, including process recreation between screens.
        preferences.edit { putString("cases.v1", rows.toString()); remove("draft.v1") }
        return true
    }

    private fun encodeEvidence(items: List<ComplaintEvidence>): JSONArray = JSONArray().apply {
        items.forEach { item -> put(JSONObject().apply {
            put("id", item.id); put("name", item.name); put("mime", item.mimeType); put("size", item.sizeBytes)
        }) }
    }

    private fun decodeEvidence(rows: JSONArray?): List<ComplaintEvidence> =
        if (rows == null) emptyList() else (0 until minOf(rows.length(), MAX_COMPLAINT_IMAGES)).mapNotNull { index -> runCatching {
            val row = rows.getJSONObject(index)
            evidence.restore(row.getString("id"), row.getString("name"), row.getString("mime"), row.getLong("size"))
        }.getOrNull() }.distinctBy { it.id }
}
