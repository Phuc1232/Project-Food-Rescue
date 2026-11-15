package com.foodrescue.app.feature.finance.data

import android.content.Context
import com.foodrescue.app.feature.finance.domain.*
import org.json.JSONObject

/** Owner tracking is read-only. Confirmation and transfer records belong to the settlement provider. */
internal class LocalSettlementStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("owner_settlements.v1", Context.MODE_PRIVATE)
    fun read(storeId: Int): List<RevenueSettlement> {
        val raw = preferences.getString("store.$storeId", null) ?: return emptyList()
        val root = JSONObject(raw)
        require(root.getInt("version") == 1)
        val rows = root.getJSONArray("records")
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            val bank = row.getJSONObject("destination")
            val lines = row.getJSONArray("lines")
            RevenueSettlement(row.getString("id"), row.getInt("storeId"),
                RevenueWindow(row.getLong("periodStart"), row.getLong("periodEnd")),
                (0 until lines.length()).map { i -> lines.getJSONObject(i).let {
                    SettlementLine(it.getString("orderId"), it.getString("reference"), it.getString("boxName"),
                        it.getInt("quantity"), it.getLong("gross"), it.getInt("commissionBps"), it.getLong("commission"))
                } }, SettlementStatus.valueOf(row.getString("status")),
                SettlementDestination(bank.getString("bank"), bank.getString("holder"), bank.getString("lastFour")),
                row.getLong("updatedAt"), if (row.isNull("paidAt")) null else row.getLong("paidAt"),
                if (row.isNull("transferReference")) null else row.getString("transferReference"),
                if (row.isNull("failureReason")) null else row.getString("failureReason"))
        }
    }
}
