package com.foodrescue.app.feature.menu.domain

internal enum class MenuCategory { BAKERY, MEAL, DRINK, SNACK, GROCERY, OTHER }
internal enum class MenuUnit { PORTION, PIECE, CUP, BOTTLE, BOX, PACK, KILOGRAM }
internal enum class MenuStatus { ACTIVE, INACTIVE }
internal enum class MenuSort { UPDATED, NAME, PRICE_ASC, PRICE_DESC }
internal enum class MenuField { NAME, PRICE, DESCRIPTION, DUPLICATE }
internal enum class MenuAssetIssue { UNSUPPORTED, TOO_LARGE, UNREADABLE, LIMIT }
internal const val MAX_MENU_FILE_BYTES = 10L * 1024 * 1024
internal const val MAX_MENU_DOCUMENTS = 5

internal data class MenuAsset(val id: String, val name: String, val mimeType: String, val byteCount: Long,
    val previewUri: String, val sourceUri: String, val pageCount: Int = 1)
internal data class MenuItem(val id: String, val name: String, val category: MenuCategory, val unit: MenuUnit,
    val price: Long, val description: String, val status: MenuStatus, val photo: MenuAsset?,
    val createdAtMillis: Long, val updatedAtMillis: Long)
internal data class MenuDraft(val id: String? = null, val name: String = "", val category: MenuCategory = MenuCategory.OTHER,
    val unit: MenuUnit = MenuUnit.PORTION, val price: String = "", val description: String = "",
    val status: MenuStatus = MenuStatus.ACTIVE, val photo: MenuAsset? = null)
internal data class StoreMenu(val items: List<MenuItem> = emptyList(), val documents: List<MenuAsset> = emptyList()) {
    val assetIds: Set<String> get() = (items.mapNotNull { it.photo?.id } + documents.map { it.id }).toSet()
}
internal fun MenuItem.toDraft() = MenuDraft(id, name, category, unit, price.toString(), description, status, photo)
internal class MenuValidationException(val fields: Set<MenuField>) : IllegalArgumentException()
internal class MenuAssetException(val issue: MenuAssetIssue) : Exception()

internal interface MenuRepository {
    suspend fun load(): StoreMenu
    suspend fun save(draft: MenuDraft): StoreMenu
    suspend fun setStatus(id: String, status: MenuStatus): StoreMenu
    suspend fun addDocument(asset: MenuAsset): StoreMenu
    suspend fun removeDocument(id: String): StoreMenu
}
internal interface MenuAssetStore {
    suspend fun import(sourceUri: String, allowPdf: Boolean): MenuAsset
    suspend fun remove(asset: MenuAsset)
    suspend fun prune(retainedIds: Set<String>)
}
