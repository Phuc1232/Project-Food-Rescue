package com.foodrescue.app.feature.surprisebox.data

import com.foodrescue.app.feature.surprisebox.domain.*
import org.json.JSONArray
import org.json.JSONObject

internal object OwnerBoxJson {
    fun decode(raw: String): OwnerBoxCatalog {
        val root = JSONObject(raw)
        require(root.getInt("version") == 1)
        val templates = root.getJSONArray("templates")
        val batches = root.getJSONArray("batches")
        return OwnerBoxCatalog((0 until templates.length()).map { readTemplate(templates.getJSONObject(it)) },
            (0 until batches.length()).map { index ->
                val json = batches.getJSONObject(index)
                SaleBatch(json.getString("id"), readTemplate(json.getJSONObject("template")), json.getInt("quantity"),
                    json.getLong("salesStart"), json.getLong("salesEnd"), json.getLong("pickupStart"), json.getLong("pickupEnd"),
                    BatchControl.valueOf(json.getString("control")), json.getInt("held"), json.getInt("sold"))
            })
    }
    private fun readTemplate(json: JSONObject) = BoxTemplate(json.getString("id"), json.getString("name"),
        BoxCategory.valueOf(json.getString("category")), json.getString("description"), json.getString("allergens"),
        json.getLong("value"), json.getLong("price"), json.getBoolean("active"))
    private fun templateJson(template: BoxTemplate) = JSONObject().apply {
        put("id", template.id); put("name", template.name); put("category", template.category.name)
        put("description", template.description); put("allergens", template.allergens)
        put("value", template.minimumValue); put("price", template.price); put("active", template.active)
    }
    fun encode(catalog: OwnerBoxCatalog) = JSONObject().apply {
        put("version", 1)
        put("templates", JSONArray().apply { catalog.templates.forEach { put(templateJson(it)) } })
        put("batches", JSONArray().apply { catalog.batches.forEach { batch -> put(JSONObject().apply {
            put("id", batch.id); put("template", templateJson(batch.template)); put("quantity", batch.quantity)
            put("salesStart", batch.salesStart); put("salesEnd", batch.salesEnd); put("pickupStart", batch.pickupStart); put("pickupEnd", batch.pickupEnd)
            put("control", batch.control.name); put("held", batch.held); put("sold", batch.sold)
        }) } })
    }.toString()
}
