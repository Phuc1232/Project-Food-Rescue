package com.foodrescue.app.feature.finance.data

import com.foodrescue.app.feature.finance.domain.*
import org.json.JSONArray
import org.json.JSONObject

internal object SettlementJson {
    fun decode(raw: String): List<RevenueSettlement> {
        val root = JSONObject(raw); require(root.getInt("version") == 1)
        val rows = root.getJSONArray("records")
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index); val bank = row.getJSONObject("destination")
            val lines = row.getJSONArray("lines")
            val events = row.optJSONArray("history") ?: JSONArray()
            val offsets = row.optJSONArray("adjustments") ?: JSONArray()
            RevenueSettlement(row.getString("id"), row.getInt("storeId"),
                RevenueWindow(row.getLong("periodStart"), row.getLong("periodEnd")),
                (0 until lines.length()).map { i -> lines.getJSONObject(i).let {
                    SettlementLine(it.getString("orderId"), it.getString("reference"), it.getString("boxName"),
                        it.getInt("quantity"), it.getLong("gross"), it.getInt("commissionBps"), it.getLong("commission"), it.optLong("refunded", 0))
                } }, SettlementStatus.valueOf(row.getString("status")),
                SettlementDestination(bank.getString("bank"), bank.getString("holder"), bank.getString("lastFour"), bank.textOrNull("accountNumber")),
                row.getLong("updatedAt"), row.longOrNull("paidAt"), row.textOrNull("transferReference"), row.textOrNull("failureReason"),
                row.optLong("revision", 0), (0 until events.length()).map { i -> events.getJSONObject(i).let {
                    SettlementEvent(SettlementStatus.valueOf(it.getString("status")), it.getString("actor"), it.getLong("at"), it.getString("note"))
                } }, (0 until offsets.length()).map { i -> offsets.getJSONObject(i).let {
                    SettlementAdjustment(it.getString("sourceId"), it.getString("orderId"), it.getLong("amount"))
                } })
        }
    }
    fun encode(records: List<RevenueSettlement>): String = JSONObject().apply {
        put("version", 1); put("records", JSONArray().apply { records.forEach { record -> put(JSONObject().apply {
            put("id", record.id); put("storeId", record.storeId); put("periodStart", record.period.start); put("periodEnd", record.period.endExclusive)
            put("status", record.status.name); put("updatedAt", record.updatedAt); put("paidAt", record.paidAt)
            put("transferReference", record.transferReference); put("failureReason", record.failureReason); put("revision", record.revision)
            record.destination?.let { put("destination", JSONObject().apply {
                put("bank", it.bank); put("holder", it.holder); put("lastFour", it.lastFour); put("accountNumber", it.accountNumber)
            }) }
            put("lines", JSONArray().apply { record.lines.forEach { line -> put(JSONObject().apply {
                put("orderId", line.orderId); put("reference", line.reference); put("boxName", line.boxName)
                put("quantity", line.quantity); put("gross", line.gross); put("commissionBps", line.commissionBps)
                put("commission", line.commission); put("refunded", line.refunded)
            }) } })
            put("history", JSONArray().apply { record.history.forEach { event -> put(JSONObject().apply {
                put("status", event.status.name); put("actor", event.actor); put("at", event.atMillis); put("note", event.note)
            }) } })
            put("adjustments", JSONArray().apply { record.adjustments.forEach { offset -> put(JSONObject().apply {
                put("sourceId", offset.sourceId); put("orderId", offset.orderId); put("amount", offset.amount)
            }) } })
        }) } })
    }.toString()
    private fun JSONObject.textOrNull(key: String): String? = if (isNull(key)) null else getString(key)
    private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)
}
