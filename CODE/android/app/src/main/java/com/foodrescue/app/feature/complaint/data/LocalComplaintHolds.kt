package com.foodrescue.app.feature.complaint.data

import android.content.Context
import com.foodrescue.app.feature.complaint.domain.ComplaintStatus
import org.json.JSONArray
import com.foodrescue.app.feature.order.domain.BoxReservation
import com.foodrescue.app.feature.refund.domain.*

/** Reads only order identifiers/status; evidence and customer details never enter owner reports. */
internal class LocalComplaintHolds(context: Context) {
    private val app = context.applicationContext
    fun read(accountKey: String, orderIds: Set<String>, completedRequestIds: Set<String> = emptySet()): Set<String> {
        require(accountKey.matches(Regex("[a-f0-9]{64}")))
        val preferences = app.getSharedPreferences("customer_complaints_$accountKey", Context.MODE_PRIVATE)
        val rows = JSONArray(preferences.getString("cases.v1", "[]"))
        return buildSet {
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                val id = row.getJSONObject("order").getString("id")
                val status = ComplaintStatus.valueOf(row.getString("status"))
                val request = row.optJSONObject("refundRequest")
                if (id in orderIds && (status.isOpen || (request != null && request.getString("id") !in completedRequestIds))) add(id)
            }
        }
    }
    fun queuedRefunds(accountKey: String, orders: List<BoxReservation>): Map<String, List<RefundMovement>> {
        require(accountKey.matches(Regex("[a-f0-9]{64}")))
        val rows = JSONArray(app.getSharedPreferences("customer_complaints_$accountKey", Context.MODE_PRIVATE).getString("cases.v1", "[]"))
        val pairs = (0 until rows.length()).mapNotNull { index ->
            val row = rows.getJSONObject(index)
            val order = orders.singleOrNull { it.id == row.getJSONObject("order").getString("id") } ?: return@mapNotNull null
            val request = row.optJSONObject("refundRequest") ?: return@mapNotNull null
            if (order.adminRefunds.any { it.id == request.getString("id") }) return@mapNotNull null
            order.id to RefundMovement(request.getLong("amount"), request.getLong("requestedAt"))
        }
        return pairs.groupBy({ it.first }, { it.second })
    }
}
