package com.foodrescue.app.feature.surprisebox.data

import android.annotation.SuppressLint
import android.content.Context
import com.foodrescue.app.feature.store.domain.OwnerStoreRepository
import com.foodrescue.app.feature.store.domain.StoreReviewStatus
import com.foodrescue.app.feature.surprisebox.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal class LocalOwnerBoxRepository(context: Context, email: String, private val owner: OwnerStoreRepository,
    private val clock: () -> Long = System::currentTimeMillis) : OwnerBoxRepository {
    private val key = MessageDigest.getInstance("SHA-256").digest(email.trim().lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    private val preferences = context.applicationContext.getSharedPreferences("owner_boxes_$key", Context.MODE_PRIVATE)
    private val mutex = locks.getOrPut(key) { Mutex() }
    override suspend fun load(): OwnerBoxCatalog = withContext(Dispatchers.IO) { mutex.withLock { read() } }
    override suspend fun saveTemplate(draft: BoxTemplateDraft) = change { it.saveTemplate(draft, UUID.randomUUID().toString()) }
    override suspend fun saveBatch(draft: SaleBatchDraft) = change { it.saveBatch(draft, clock(), UUID.randomUUID().toString()) }
    override suspend fun operate(id: String, action: BatchAction) = change { it.operate(id, action, clock()) }
    override suspend fun setTemplateActive(id: String, active: Boolean) = change { catalog ->
        check(catalog.templates.any { it.id == id })
        catalog.copy(templates = catalog.templates.map { if (it.id == id) it.copy(active = active) else it })
    }

    @SuppressLint("UseKtx")
    private suspend fun change(update: (OwnerBoxCatalog) -> OwnerBoxCatalog): OwnerBoxCatalog = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(owner.load().status == StoreReviewStatus.APPROVED) { "Store must be approved" }
            val result = update(read())
            check(preferences.edit().putString("catalog.v1", encode(result)).commit()) { "Catalog write failed" }
            result
        }
    }
    private fun read(): OwnerBoxCatalog {
        val raw = preferences.getString("catalog.v1", null) ?: return OwnerBoxCatalog()
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
    private fun encode(catalog: OwnerBoxCatalog) = JSONObject().apply {
        put("version", 1)
        put("templates", JSONArray().apply { catalog.templates.forEach { put(templateJson(it)) } })
        put("batches", JSONArray().apply { catalog.batches.forEach { batch -> put(JSONObject().apply {
            put("id", batch.id); put("template", templateJson(batch.template)); put("quantity", batch.quantity)
            put("salesStart", batch.salesStart); put("salesEnd", batch.salesEnd); put("pickupStart", batch.pickupStart); put("pickupEnd", batch.pickupEnd)
            put("control", batch.control.name); put("held", batch.held); put("sold", batch.sold)
        }) } })
    }.toString()
    private companion object { val locks = ConcurrentHashMap<String, Mutex>() }
}
