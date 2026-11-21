package com.foodrescue.app.feature.complaint.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.edit
import com.foodrescue.app.feature.complaint.domain.*
import com.foodrescue.app.core.datastore.LocalCommerceLock
import org.json.JSONObject

/** Frontend history and drafts. New requests stay pending until a response is supplied. */
internal class LocalComplaintRepository(context: Context, email: String, private val evidence: LocalEvidenceStore,
    private val online: (() -> Boolean)? = null) : ComplaintRepository {
    private val account = complaintAccountKey(email)
    private val records = LocalComplaintRecords(context)
    private val orders = com.foodrescue.app.feature.order.data.LocalOrderLedger(context)
    private val preferences = records.preferences(account)
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    override fun load(): List<CustomerComplaint> = synchronized(LocalCommerceLock) {
        runCatching {
            val history = orders.read().filter { it.account == account }.associateBy { it.order.id }
            records.read(account).map { item -> item.copy(refundTransaction = history[item.order.id]?.order?.adminRefunds
                ?.singleOrNull { it.id == item.refundRequest?.id }) }
        }.getOrDefault(emptyList())
    }

    override fun loadDraft(): ComplaintDraft? = runCatching {
        val text = preferences.getString("draft.v1", null) ?: return null
        val row = JSONObject(text)
        ComplaintDraft(row.getString("id"), row.optString("orderId").takeIf(String::isNotBlank),
            row.optString("reason").takeIf(String::isNotBlank)?.let(ComplaintReason::valueOf),
            row.optString("description").take(MAX_COMPLAINT_LENGTH), ComplaintJson.decodeEvidence(row.optJSONArray("evidence"), evidence))
    }.getOrNull()

    override fun saveDraft(draft: ComplaintDraft?) = synchronized(LocalCommerceLock) {
        preferences.edit {
            if (draft == null) remove("draft.v1")
            else putString("draft.v1", JSONObject().apply {
                put("id", draft.id); put("orderId", draft.orderId); put("reason", draft.reason?.name)
                put("description", draft.description); put("evidence", ComplaintJson.encodeEvidence(draft.evidence))
            }.toString())
        }
    }

    override fun submit(complaint: CustomerComplaint): Boolean = synchronized(LocalCommerceLock) {
        val current = runCatching { records.read(account) }.getOrElse { return@synchronized false }
        current.firstOrNull { it.id == complaint.id }?.let { existing ->
            return@synchronized existing.order == complaint.order && existing.description == complaint.description &&
                existing.reason == complaint.reason && existing.evidence == complaint.evidence
        }
        if (complaint.status != ComplaintStatus.PENDING || complaint.revision != 0L || complaint.history.isNotEmpty() ||
            complaint.refundRequest != null || complaint.response != null) return@synchronized false
        if (current.any { it.order.id == complaint.order.id && it.status.isOpen }) return@synchronized false
        val available = online?.invoke() ?: (connectivity?.getNetworkCapabilities(connectivity.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true)
        if (!available) return@synchronized false
        runCatching { records.write(account, listOf(complaint) + current, removeDraft = true) }.isSuccess
    }

}
