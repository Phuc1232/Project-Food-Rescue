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
import com.foodrescue.app.core.datastore.LocalCommerceLock
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal class LocalOwnerBoxRepository(context: Context, email: String, private val owner: OwnerStoreRepository,
    private val stock: BoxStockSource? = null, private val storeId: Int = 0,
    private val packedStock: PackedBoxStockSource = LocalPackedBoxStore(context),
    private val clock: () -> Long = System::currentTimeMillis) : OwnerBoxRepository {
    private val key = MessageDigest.getInstance("SHA-256").digest(email.trim().lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    private val preferences = context.applicationContext.getSharedPreferences("owner_boxes_$key", Context.MODE_PRIVATE)
    private val mutex = locks.getOrPut(key) { Mutex() }
    override suspend fun load(): OwnerBoxCatalog = withContext(Dispatchers.IO) { mutex.withLock { synchronized(LocalCommerceLock) { read() } } }
    internal suspend fun <T> withCurrentCatalog(block: (OwnerBoxCatalog) -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock { synchronized(LocalCommerceLock) { block(read()) } }
    }
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
            synchronized(LocalCommerceLock) {
            val result = update(read())
            check(preferences.edit().putString("catalog.v1", OwnerBoxJson.encode(result)).commit()) { "Catalog write failed" }
            result
            }
        }
    }
    private fun read(): OwnerBoxCatalog {
        val raw = preferences.getString("catalog.v1", null) ?: return OwnerBoxCatalog()
        val catalog = OwnerBoxJson.decode(raw)
        return catalog.copy(batches = catalog.batches.map { batch ->
            val now = clock()
            val counts = stock?.stock(storeId, batch.id, now)
            val prepared = packedStock.packedStock(storeId, batch.id, now)
            batch.copy(held = counts?.held ?: batch.held, sold = counts?.sold ?: batch.sold,
                packed = prepared.packed, eligible = prepared.eligible, handedOver = prepared.handedOver,
                rejected = prepared.rejected, expired = prepared.expired, recheck = prepared.recheck)
        })
    }
    private companion object { val locks = ConcurrentHashMap<String, Mutex>() }
}
