// AC-09,10,13,15: AdminStoreDetail responsibility.
package com.foodrescue.backend.store.dto

/** UC-77 steps 1-2: store, owner, sale batches and orders by status, recent history (newest 50). */
data class AdminStoreDetail(
    val profile: StoreProfileView, val owner: AccountSummary, val saleBatchCounts: Map<String, Long>,
    val orderCounts: Map<String, Long>, val recentHistory: List<StoreHistoryItem>, val suspensionOrderSummary: StoreSuspensionOrderSummary? = null
)
