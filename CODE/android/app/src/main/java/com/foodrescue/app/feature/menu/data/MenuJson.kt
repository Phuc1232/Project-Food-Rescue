package com.foodrescue.app.feature.menu.data

import com.foodrescue.app.feature.menu.domain.*
import org.json.JSONArray
import org.json.JSONObject

internal object MenuJson {
    fun decode(raw: String, assets: LocalMenuAssetStore): StoreMenu {
        val root = JSONObject(raw)
        require(root.getInt("version") == 1)
        val items = root.getJSONArray("items")
        val docs = root.getJSONArray("documents")
        return StoreMenu((0 until items.length()).map { index ->
            val json = items.getJSONObject(index)
            MenuItem(json.getString("id"), json.getString("name"), MenuCategory.valueOf(json.getString("category")),
                MenuUnit.valueOf(json.getString("unit")), json.getLong("price"), json.getString("description"),
                MenuStatus.valueOf(json.getString("status")), json.optJSONObject("photo")?.let { readAsset(it, assets) },
                json.getLong("created"), json.getLong("updated"))
        }, (0 until docs.length()).map { readAsset(docs.getJSONObject(it), assets) })
    }

    private fun readAsset(json: JSONObject, assets: LocalMenuAssetStore) = assets.restore(json.getString("id"), json.getString("name"),
        json.getString("mime"), json.getLong("bytes"), json.optInt("pages", 1))

    private fun assetJson(asset: MenuAsset) = JSONObject().apply {
        put("id", asset.id); put("name", asset.name); put("mime", asset.mimeType); put("bytes", asset.byteCount); put("pages", asset.pageCount)
    }

    fun encode(menu: StoreMenu) = JSONObject().apply {
        put("version", 1)
        put("items", JSONArray().apply { menu.items.forEach { item -> put(JSONObject().apply {
            put("id", item.id); put("name", item.name); put("category", item.category.name); put("unit", item.unit.name)
            put("price", item.price); put("description", item.description); put("status", item.status.name)
            put("created", item.createdAtMillis); put("updated", item.updatedAtMillis)
            item.photo?.let { put("photo", assetJson(it)) }
        }) } })
        put("documents", JSONArray().apply { menu.documents.forEach { put(assetJson(it)) } })
    }.toString()

}
