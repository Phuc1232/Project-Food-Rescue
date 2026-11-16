package com.foodrescue.app.feature.surprisebox.domain

import com.foodrescue.app.feature.menu.domain.MenuUnit

internal enum class BoxStorage { AMBIENT, CHILLED, FROZEN }
internal enum class PackingIssue { ACCESS, BATCH, CAPACITY, ITEMS, MENU_CHANGED, VALUE, EXPIRY, STORAGE, INSPECTION }
internal class PackingException(val issues: Set<PackingIssue>) : IllegalArgumentException()

internal data class PackingSelection(val itemId: String, val quantity: String, val unitPrice: Long,
    val unit: MenuUnit, val updatedAt: Long)
internal data class PackingDraft(val id: String = "", val batchId: String = "",
    val selections: List<PackingSelection> = emptyList(), val expiresAt: Long = 0, val storage: BoxStorage? = null,
    val foodChecked: Boolean = false, val packagingChecked: Boolean = false, val allergensChecked: Boolean = false)
internal data class PackedItem(val itemId: String, val name: String, val unit: MenuUnit,
    val unitPrice: Long, val quantity: String, val value: Long)
/** Delivery is projected from the atomic order ledger, never written over the packing receipt. */
internal data class PackedBoxHandover(val orderId: String, val reference: String, val atMillis: Long)
internal data class PackedBoxNoShow(val orderId: String, val reference: String, val requestId: String, val atMillis: Long)
/** Immutable receipt: commercial and menu changes cannot rewrite what was actually packed. */
internal data class PackedBox(val id: String, val storeId: Int, val batchId: String, val templateName: String,
    val minimumValue: Long, val allergens: String, val items: List<PackedItem>, val expiresAt: Long,
    val storage: BoxStorage, val preparedById: String, val preparedByName: String, val packedAt: Long,
    val handover: PackedBoxHandover? = null, val inspections: List<BoxInspection> = emptyList(), val noShow: PackedBoxNoShow? = null) {
    val value: Long get() = items.sumOf { it.value }
    val code: String get() = "BOX-${id.replace("-", "").take(12).uppercase(java.util.Locale.ROOT)}"
}
internal data class PackedBoxStock(val packed: Int = 0, val eligible: Int = 0, val handedOver: Int = 0,
    val rejected: Int = 0, val expired: Int = 0, val recheck: Int = 0)
internal fun interface PackedBoxStockSource { fun packedStock(storeId: Int, batchId: String, now: Long): PackedBoxStock }
