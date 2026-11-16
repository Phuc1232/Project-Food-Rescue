package com.foodrescue.app.feature.surprisebox.data

import com.foodrescue.app.feature.menu.domain.MenuUnit
import com.foodrescue.app.feature.surprisebox.domain.*
import org.json.JSONArray
import org.json.JSONObject

internal object PackedBoxJson {
    fun encode(boxes: List<PackedBox>): String = JSONObject().apply {
        put("version", 3)
        put("boxes", JSONArray().apply { boxes.forEach { box -> put(JSONObject().apply {
            put("id", box.id); put("store", box.storeId); put("batch", box.batchId); put("template", box.templateName)
            put("minimum", box.minimumValue); put("allergens", box.allergens); put("expires", box.expiresAt)
            put("storage", box.storage.name); put("employee", box.preparedById); put("employeeName", box.preparedByName); put("packedAt", box.packedAt)
            put("inspections", JSONArray().apply { box.inspections.forEach { event -> put(JSONObject().apply {
                put("id", event.id); put("result", event.result.name); put("reason", event.reason?.name ?: JSONObject.NULL)
                put("note", event.note); put("employee", event.employeeId); put("employeeName", event.employeeName); put("at", event.atMillis)
                put("noShowRequest", event.noShowRequestId ?: JSONObject.NULL)
            }) } })
            put("items", JSONArray().apply { box.items.forEach { item -> put(JSONObject().apply {
                put("id", item.itemId); put("name", item.name); put("unit", item.unit.name); put("price", item.unitPrice)
                put("quantity", item.quantity); put("value", item.value)
            }) } })
        }) } })
    }.toString()
    fun decode(raw: String): List<PackedBox> {
        val root = JSONObject(raw); val version = root.getInt("version"); require(version in 1..3)
        val rows = root.getJSONArray("boxes")
        return (0 until rows.length()).map { index ->
            val box = rows.getJSONObject(index); val items = box.getJSONArray("items")
            PackedBox(box.getString("id"), box.getInt("store"), box.getString("batch"), box.getString("template"),
                box.getLong("minimum"), box.getString("allergens"), (0 until items.length()).map { i ->
                    val item = items.getJSONObject(i)
                    PackedItem(item.getString("id"), item.getString("name"), MenuUnit.valueOf(item.getString("unit")),
                        item.getLong("price"), item.getString("quantity"), item.getLong("value"))
                }, box.getLong("expires"), BoxStorage.valueOf(box.getString("storage")),
                box.getString("employee"), box.getString("employeeName"), box.getLong("packedAt"),
                inspections = if (version == 1) emptyList() else decodeInspections(box, version))
        }
    }
    private fun decodeInspections(box: JSONObject, version: Int): List<BoxInspection> {
        val rows = box.getJSONArray("inspections")
        val events = (0 until rows.length()).map { index ->
            val event = rows.getJSONObject(index)
            if (version >= 3) require(event.has("noShowRequest"))
            BoxInspection(event.getString("id"), InspectionResult.valueOf(event.getString("result")),
                if (event.isNull("reason")) null else InspectionReason.valueOf(event.getString("reason")),
                event.getString("note"), event.getString("employee"), event.getString("employeeName"), event.getLong("at"),
                if (event.isNull("noShowRequest")) null else event.getString("noShowRequest").also { require(it.isNotBlank()) })
        }
        require(events.map { it.id }.distinct().size == events.size)
        events.forEachIndexed { index, event ->
            require(event.id.isNotBlank() && event.employeeId.isNotBlank() && event.employeeName.isNotBlank())
            require(event.atMillis >= (events.getOrNull(index - 1)?.atMillis ?: box.getLong("packedAt")))
            require(event.note.length <= 500 && event.note.none { it.isISOControl() && it != '\n' })
            require((event.result == InspectionResult.PASSED && event.reason == null && event.atMillis < box.getLong("expires")) ||
                (event.result == InspectionResult.REJECTED && event.reason != null && index == events.lastIndex))
        }
        return events
    }
}
