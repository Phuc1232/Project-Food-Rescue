package com.foodrescue.app.feature.store.domain

import com.foodrescue.app.feature.menu.domain.StoreMenu
import com.foodrescue.app.feature.surprisebox.domain.OwnerBoxCatalog
import com.foodrescue.app.feature.surprisebox.domain.PackedBox
import com.foodrescue.app.feature.surprisebox.domain.SaleBatch

internal enum class StoreRestrictionAction { SUSPEND_STORE, RESTORE_STORE, STOP_BATCH, RELEASE_BATCH }

/** Restrictions are independent of application approval and never rewrite commercial/packing receipts. */
internal data class StoreRestrictionEvent(val id: String, val action: StoreRestrictionAction,
    val batchId: String?, val batchName: String?, val note: String, val actor: String, val atMillis: Long)

internal data class StoreGovernance(val events: List<StoreRestrictionEvent> = emptyList()) {
    init {
        require(events.distinctBy { it.id }.size == events.size)
        var suspended = false
        val blocked = mutableSetOf<String>()
        events.forEachIndexed { index, event ->
            require(event.id.isNotBlank() && event.actor.isNotBlank() && validRestrictionNote(event.note))
            require(event.atMillis >= (events.getOrNull(index - 1)?.atMillis ?: 0))
            when (event.action) {
                StoreRestrictionAction.SUSPEND_STORE -> { require(event.batchId == null && !suspended); suspended = true }
                StoreRestrictionAction.RESTORE_STORE -> { require(event.batchId == null && suspended); suspended = false }
                StoreRestrictionAction.STOP_BATCH -> { require(!event.batchId.isNullOrBlank() && blocked.add(event.batchId)) }
                StoreRestrictionAction.RELEASE_BATCH -> { require(!event.batchId.isNullOrBlank() && blocked.remove(event.batchId)) }
            }
        }
    }
    val suspension: StoreRestrictionEvent? get() = events.lastOrNull { it.batchId == null }
        ?.takeIf { it.action == StoreRestrictionAction.SUSPEND_STORE }
    val blockedBatches: Map<String, StoreRestrictionEvent> get() = events.filter { it.batchId != null }
        .associateBy { checkNotNull(it.batchId) }.filterValues { it.action == StoreRestrictionAction.STOP_BATCH }
    fun permitsNewSales(batchId: String) = suspension == null && batchId !in blockedBatches
}

internal data class StoreRestrictionDraft(val id: String, val expectedRevision: Long,
    val action: StoreRestrictionAction, val batch: SaleBatch? = null, val note: String = "")
internal class StaleStoreSupervision : IllegalStateException()
internal class RestrictedStoreSale : IllegalStateException()

internal fun validRestrictionNote(note: String) = note.trim().length in 10..500 &&
    note.none { it.isISOControl() && it != '\n' }

internal fun OwnerStore.restrict(draft: StoreRestrictionDraft, actor: String, now: Long): OwnerStore {
    require(draft.id.isNotBlank() && actor.isNotBlank() && validRestrictionNote(draft.note))
    if (status != StoreReviewStatus.APPROVED || revision != draft.expectedRevision ||
        governance.events.any { it.id == draft.id }) throw StaleStoreSupervision()
    require(now >= (governance.events.lastOrNull()?.atMillis ?: 0))
    val batch = draft.batch
    val valid = when (draft.action) {
        StoreRestrictionAction.SUSPEND_STORE -> batch == null && governance.suspension == null
        StoreRestrictionAction.RESTORE_STORE -> batch == null && governance.suspension != null
        StoreRestrictionAction.STOP_BATCH -> batch != null && batch.id !in governance.blockedBatches
        StoreRestrictionAction.RELEASE_BATCH -> batch != null && batch.id in governance.blockedBatches
    }
    if (!valid) throw StaleStoreSupervision()
    return copy(governance = StoreGovernance(governance.events + StoreRestrictionEvent(draft.id,
        draft.action, batch?.id, batch?.template?.name, draft.note.trim(), actor, now)))
}

internal data class SupervisedStore(val accountKey: String, val storeId: Int, val store: OwnerStore) {
    val reference: String get() = "CH-${kotlin.math.abs(storeId.toLong())}"
}
internal data class StoreActivity(val id: String, val atMillis: Long, val title: String, val detail: String)
internal data class StoreSupervisionDetail(val entry: SupervisedStore, val menu: StoreMenu,
    val catalog: OwnerBoxCatalog, val boxes: List<PackedBox>, val activity: List<StoreActivity>,
    val paidOrders: Int, val activeHolds: Int, val loadedAt: Long)

internal interface StoreSupervisionRepository {
    suspend fun stores(): List<SupervisedStore>
    suspend fun detail(accountKey: String): StoreSupervisionDetail
    suspend fun decide(accountKey: String, draft: StoreRestrictionDraft): SupervisedStore
}
