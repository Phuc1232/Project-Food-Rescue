package com.foodrescue.app.feature.menu.data

import android.annotation.SuppressLint
import android.content.Context
import com.foodrescue.app.feature.menu.domain.*
import com.foodrescue.app.feature.store.domain.OwnerStoreRepository
import com.foodrescue.app.feature.store.domain.StoreReviewStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal fun menuAccountKey(email: String) = MessageDigest.getInstance("SHA-256")
    .digest(email.trim().lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

internal class LocalMenuRepository(context: Context, email: String, private val owner: OwnerStoreRepository,
    private val assets: LocalMenuAssetStore) : MenuRepository {
    private val key = menuAccountKey(email)
    private val preferences = context.applicationContext.getSharedPreferences("owner_menu_$key", Context.MODE_PRIVATE)
    private val mutex = locks.getOrPut(key) { Mutex() }

    override suspend fun load(): StoreMenu = withContext(Dispatchers.IO) { mutex.withLock { read() } }
    internal suspend fun <T> withCurrentMenu(block: suspend (StoreMenu) -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock { block(read()) }
    }

    override suspend fun save(draft: MenuDraft): StoreMenu = change { menu ->
        val errors = draft.validationErrors(menu.items)
        if (errors.isNotEmpty()) throw MenuValidationException(errors)
        val previous = draft.id?.let { id -> menu.items.firstOrNull { it.id == id } ?: error("Menu item no longer exists") }
        draft.photo?.let { if (!it.mimeType.startsWith("image/") || !assets.isAvailable(it)) throw MenuAssetException(MenuAssetIssue.UNREADABLE) }
        val now = System.currentTimeMillis()
        val item = MenuItem(previous?.id ?: UUID.randomUUID().toString(), draft.name.trim(), draft.category, draft.unit,
            draft.price.trim().toLong(), draft.description.trim(), draft.status, draft.photo, previous?.createdAtMillis ?: now, now)
        menu.copy(items = if (previous == null) menu.items + item else menu.items.map { if (it.id == item.id) item else it })
    }

    override suspend fun setStatus(id: String, status: MenuStatus) = change { menu ->
        check(menu.items.any { it.id == id })
        menu.copy(items = menu.items.map { if (it.id == id) it.copy(status = status, updatedAtMillis = System.currentTimeMillis()) else it })
    }

    override suspend fun addDocument(asset: MenuAsset) = change { menu ->
        if (menu.documents.size >= MAX_MENU_DOCUMENTS) throw MenuAssetException(MenuAssetIssue.LIMIT)
        if (!assets.isAvailable(asset)) throw MenuAssetException(MenuAssetIssue.UNREADABLE)
        check(menu.documents.none { it.id == asset.id })
        menu.copy(documents = menu.documents + asset)
    }

    override suspend fun removeDocument(id: String) = change { menu -> menu.copy(documents = menu.documents.filterNot { it.id == id }) }

    @SuppressLint("UseKtx")
    private suspend fun change(update: (StoreMenu) -> StoreMenu): StoreMenu = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(owner.load().status == StoreReviewStatus.APPROVED) { "Store must be approved" }
            val before = read()
            val after = update(before)
            check(preferences.edit().putString("menu.v1", MenuJson.encode(after)).commit()) { "Menu write failed" }
            // Cleanup never changes the result of a successful metadata commit.
            val removed = (before.items.mapNotNull { it.photo } + before.documents).filter { it.id !in after.assetIds }
            removed.forEach { assets.remove(it) }
            after
        }
    }

    private fun read(): StoreMenu = preferences.getString("menu.v1", null)?.let { MenuJson.decode(it, assets) } ?: StoreMenu()

    companion object {
        private val locks = ConcurrentHashMap<String, Mutex>()
        /** Account-scoped read uses the same mutex and codec as owner edits; it cannot create or prune assets. */
        suspend fun readForAccount(context: Context, key: String): StoreMenu = withContext(Dispatchers.IO) {
            require(key.matches(Regex("[0-9a-f]{64}")))
            locks.getOrPut(key) { Mutex() }.withLock {
                context.getSharedPreferences("owner_menu_$key", Context.MODE_PRIVATE).getString("menu.v1", null)
                    ?.let { MenuJson.decode(it, LocalMenuAssetStore.forAccountKey(context, key)) } ?: StoreMenu()
            }
        }
    }
}
