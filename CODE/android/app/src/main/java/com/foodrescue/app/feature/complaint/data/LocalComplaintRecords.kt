package com.foodrescue.app.feature.complaint.data

import android.annotation.SuppressLint
import android.content.Context
import com.foodrescue.app.core.datastore.LocalCommerceLock
import com.foodrescue.app.feature.complaint.domain.CustomerComplaint
import java.io.File

internal class LocalComplaintRecords(context: Context) {
    private val app = context.applicationContext
    fun preferences(account: String) = app.getSharedPreferences("customer_complaints_" + checkedKey(account), Context.MODE_PRIVATE)
    fun accounts(): List<String> {
        val directory = File(app.applicationInfo.dataDir, "shared_prefs")
        if (!directory.exists()) return emptyList()
        return checkNotNull(directory.listFiles()).mapNotNull {
            Regex("customer_complaints_([a-f0-9]{64})\\.xml").matchEntire(it.name)?.groupValues?.get(1)
        }.sorted()
    }
    fun read(account: String): List<CustomerComplaint> = synchronized(LocalCommerceLock) {
        ComplaintJson.decode(preferences(account).getString("cases.v1", "[]")!!, LocalEvidenceStore.forAccountKey(app, account))
    }
    // A decision, audit event and queued refund share a single durable write. Preserve the draft.
    @SuppressLint("UseKtx", "ApplySharedPref")
    fun write(account: String, cases: List<CustomerComplaint>, removeDraft: Boolean = false) = synchronized(LocalCommerceLock) {
        val prefs = preferences(account)
        val before = prefs.getString("cases.v1", null)
        val draft = prefs.getString("draft.v1", null)
        val edit = prefs.edit().putString("cases.v1", ComplaintJson.encode(cases))
        if (removeDraft) edit.remove("draft.v1")
        if (!edit.commit()) {
            prefs.edit().putString("cases.v1", before).putString("draft.v1", draft).commit()
            error("Complaint write failed")
        }
    }
    private fun checkedKey(key: String): String { require(key.matches(Regex("[a-f0-9]{64}"))); return key }
}
