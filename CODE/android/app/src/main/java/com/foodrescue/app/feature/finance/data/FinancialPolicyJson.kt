package com.foodrescue.app.feature.finance.data

import com.foodrescue.app.feature.finance.domain.*
import org.json.JSONArray
import org.json.JSONObject

internal object FinancialPolicyJson {
    fun terms(row: JSONObject) = FinancialTerms(row.getLong("version"), row.getInt("commissionBps"),
        row.getInt("cancellationMinutes"), row.getInt("cancellationRefundBps"),
        row.getInt("noShowGraceMinutes"), row.getInt("noShowRefundBps"))
    fun terms(value: FinancialTerms) = JSONObject().apply {
        put("version", value.version); put("commissionBps", value.commissionBps)
        put("cancellationMinutes", value.cancellationMinutes); put("cancellationRefundBps", value.cancellationRefundBps)
        put("noShowGraceMinutes", value.noShowGraceMinutes); put("noShowRefundBps", value.noShowRefundBps)
    }
    fun decode(raw: String): FinancialPolicyBook {
        val root = JSONObject(raw); require(root.getInt("schema") == 1)
        val rows = root.getJSONArray("versions")
        return FinancialPolicyBook((0 until rows.length()).map { rows.getJSONObject(it).let { row ->
            FinancialPolicy(terms(row.getJSONObject("terms")), row.getLong("effectiveAt"),
                row.getLong("publishedAt"), row.getString("actor"), row.getString("reason"))
        } })
    }
    fun encode(book: FinancialPolicyBook): String = JSONObject().apply {
        put("schema", 1); put("versions", JSONArray().apply { book.versions.forEach { value -> put(JSONObject().apply {
            put("terms", terms(value.terms)); put("effectiveAt", value.effectiveAt); put("publishedAt", value.publishedAt)
            put("actor", value.actor); put("reason", value.reason)
        }) } })
    }.toString()
}
