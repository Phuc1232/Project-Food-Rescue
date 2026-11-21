package com.foodrescue.app.feature.order.data

import com.foodrescue.app.core.common.PickupLocation
import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.payment.domain.*
import com.foodrescue.app.feature.refund.domain.*
import com.foodrescue.app.feature.refund.data.ManagedRefundJson
import com.foodrescue.app.feature.surprisebox.domain.*
import org.json.JSONObject
import org.json.JSONArray

internal object OrderJson {
    fun read(row: JSONObject): BoxReservation = BoxReservation(
        row.getString("id"), row.getInt("boxId"), row.getInt("quantity"),
        PickupWindow(row.getLong("pickupStart"), row.getLong("pickupEnd")), row.getLong("createdAt"), row.getLong("expiresAt"), row.getInt("unitPrice"),
        row.optJSONObject("payment")?.let { QrPayment(it.getString("reference"), it.getLong("amount"), PaymentStatus.valueOf(it.getString("status")), it.getLong("updatedAt")) },
        row.longOrNull("cancelledAt"), row.longOrNull("collectedAt"),
        row.optString("cancellationReason").takeIf { it.isNotBlank() && it != "null" }?.let(CancellationReason::valueOf), row.optString("cancellationNote"),
        row.optJSONObject("refund")?.let { OrderRefund(it.getLong("amount"), RefundStatus.valueOf(it.getString("status")), it.getLong("requestedAt"), it.getLong("updatedAt")) },
        row.optJSONObject("box")?.let(::readBox),
        row.optJSONObject("ownerCancellation")?.let { OwnerCancellationRequest(OwnerCancellationReason.valueOf(it.getString("reason")), it.getString("note"), it.getLong("requestedAt")) },
        row.optJSONObject("handover")?.let { receipt ->
            val ids = receipt.getJSONArray("boxes")
            HandoverReceipt(receipt.getString("request"), (0 until ids.length()).map(ids::getString),
                receipt.getString("employee"), receipt.getString("employeeName"), receipt.getLong("at"))
        },
        row.optJSONArray("handoverFailures")?.let { entries -> (0 until entries.length()).map { index ->
            val event = entries.getJSONObject(index)
            HandoverFailure(event.getString("id"), HandoverFailureReason.valueOf(event.getString("reason")), event.getString("note"),
                event.getString("employee"), event.getString("employeeName"), event.getLong("at"))
        } }.orEmpty(),
        if (row.isNull("noShow")) null else row.getJSONObject("noShow").let { receipt ->
            val ids = receipt.getJSONArray("boxes")
            NoShowReceipt(receipt.getString("request"), (0 until ids.length()).map(ids::getString), receipt.getString("note"),
                receipt.getString("employee"), receipt.getString("employeeName"), receipt.getLong("at"))
        },
        ManagedRefundJson.read(row.optJSONArray("adminRefunds")),
        row.optJSONObject("financialTerms")?.let(com.foodrescue.app.feature.finance.data.FinancialPolicyJson::terms),
    )
    fun write(order: BoxReservation) = JSONObject().apply {
        order.financialTerms?.let { put("financialTerms", com.foodrescue.app.feature.finance.data.FinancialPolicyJson.terms(it)) }
        if (order.adminRefunds.isNotEmpty()) put("adminRefunds", ManagedRefundJson.write(order.adminRefunds))
        put("id", order.id); put("boxId", order.boxId); put("quantity", order.quantity); put("unitPrice", order.unitPrice)
        put("pickupStart", order.pickup.startMillis); put("pickupEnd", order.pickup.endMillis)
        put("createdAt", order.createdAtMillis); put("expiresAt", order.expiresAtMillis)
        put("cancelledAt", order.cancelledAtMillis); put("collectedAt", order.collectedAtMillis)
        put("cancellationReason", order.cancellationReason?.name); put("cancellationNote", order.cancellationNote)
        order.payment?.let { put("payment", JSONObject().apply {
            put("reference", it.reference); put("amount", it.amount); put("status", it.status.name); put("updatedAt", it.updatedAtMillis)
        }) }
        order.refund?.let { put("refund", JSONObject().apply {
            put("amount", it.amount); put("status", it.status.name); put("requestedAt", it.requestedAtMillis); put("updatedAt", it.updatedAtMillis)
        }) }
        order.ownerCancellation?.let { put("ownerCancellation", JSONObject().apply {
            put("reason", it.reason.name); put("note", it.note); put("requestedAt", it.requestedAtMillis)
        }) }
        order.boxSnapshot?.let { put("box", writeBox(it)) }
        order.noShow?.let { put("noShow", JSONObject().apply {
            put("request", it.requestId); put("boxes", JSONArray(it.boxIds)); put("note", it.note)
            put("employee", it.employeeId); put("employeeName", it.employeeName); put("at", it.atMillis)
        }) }
        order.handover?.let { put("handover", JSONObject().apply {
            put("request", it.requestId); put("boxes", JSONArray(it.boxIds)); put("employee", it.employeeId)
            put("employeeName", it.employeeName); put("at", it.atMillis)
        }) }
        if (order.handoverFailures.isNotEmpty()) put("handoverFailures", JSONArray().apply {
            order.handoverFailures.forEach { event -> put(JSONObject().apply {
                put("id", event.id); put("reason", event.reason.name); put("note", event.note)
                put("employee", event.employeeId); put("employeeName", event.employeeName); put("at", event.atMillis)
            }) }
        })
    }
    private fun writeBox(box: CatalogBox) = JSONObject().apply {
        put("id", box.id); put("storeId", box.storeId); put("store", box.store); put("name", box.name); put("category", box.category.name)
        put("price", box.price); put("minimumValue", box.minimumValue); put("remaining", box.remaining); put("pickup", box.pickup)
        put("description", box.description); put("allergens", box.allergens); put("district", box.district); put("distance", box.distanceMeters)
        put("hour", box.pickupStartHour); put("endHour", box.pickupEndHour); put("initials", box.initials); put("batchId", box.batchId)
        put("salesStart", box.salesStartMillis); put("salesEnd", box.salesEndMillis); put("pickupStart", box.pickupStartMillis); put("pickupEnd", box.pickupEndMillis)
        box.location?.let { put("location", JSONObject().apply { put("latitude", it.latitude); put("longitude", it.longitude); put("address", it.address) }) }
    }
    private fun readBox(row: JSONObject) = CatalogBox(row.getInt("id"), row.getString("store"), BoxCategory.valueOf(row.getString("category")),
        row.getString("name"), row.getInt("price"), row.getInt("minimumValue"), row.getInt("remaining"), row.getString("pickup"),
        row.getString("description"), row.getString("allergens"), row.getString("district"), row.getInt("distance"), row.getInt("hour"), row.getString("initials"),
        row.optJSONObject("location")?.let { PickupLocation(it.getDouble("latitude"), it.getDouble("longitude"), it.getString("address")) },
        row.getInt("storeId"), row.getInt("endHour"), row.optString("batchId").takeIf { it.isNotBlank() && it != "null" },
        row.longOrNull("salesStart"), row.longOrNull("salesEnd"), row.longOrNull("pickupStart"), row.longOrNull("pickupEnd"))
    private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)
}
