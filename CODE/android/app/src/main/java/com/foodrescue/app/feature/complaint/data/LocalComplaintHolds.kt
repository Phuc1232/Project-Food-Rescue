package com.foodrescue.app.feature.complaint.data

import android.content.Context
import com.foodrescue.app.feature.complaint.domain.ComplaintStatus
import org.json.JSONArray

/** Reads only order identifiers/status; evidence and customer details never enter owner reports. */
internal class LocalComplaintHolds(context: Context) {
    private val app = context.applicationContext
    fun read(accountKey: String, orderIds: Set<String>): Set<String> {
        require(accountKey.matches(Regex("[a-f0-9]{64}")))
        val preferences = app.getSharedPreferences("customer_complaints_$accountKey", Context.MODE_PRIVATE)
        val rows = JSONArray(preferences.getString("cases.v1", "[]"))
        return buildSet {
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                val id = row.getJSONObject("order").getString("id")
                val status = ComplaintStatus.valueOf(row.getString("status"))
                if (id in orderIds && status.isOpen) add(id)
            }
        }
    }
}
