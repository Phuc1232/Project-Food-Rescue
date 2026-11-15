package com.foodrescue.app.feature.finance.presentation

import com.foodrescue.app.feature.finance.domain.*
import com.foodrescue.app.feature.surprisebox.domain.boxSearchKey

internal enum class RevenuePage { OVERVIEW, TRANSACTIONS, TRANSACTION, SETTLEMENTS, SETTLEMENT }
internal enum class RevenueBasis { PAYMENT, COLLECTION }
internal enum class RevenueFilter { ALL, ELIGIBLE, WAITING, HELD, REFUNDS }
internal enum class SettlementFilter { ALL, AWAITING, PROCESSING, PAID, FAILED }
internal data class RevenueUiState(
    val snapshot: RevenueSnapshot? = null, val loading: Boolean = true, val error: String? = null,
    val now: Long = System.currentTimeMillis(), val period: RevenuePeriod = RevenuePeriod.WEEK, val offset: Int = 0,
    val page: RevenuePage = RevenuePage.OVERVIEW, val basis: RevenueBasis = RevenueBasis.PAYMENT,
    val filter: RevenueFilter = RevenueFilter.ALL, val query: String = "",
    val settlementFilter: SettlementFilter = SettlementFilter.ALL,
    val entryId: String? = null, val settlementId: String? = null,
    val entryFromSettlement: Boolean = false, val settlementFromEntry: Boolean = false,
) {
    val window: RevenueWindow get() = period.window(now, offset)
    val report: RevenueReport? get() = snapshot?.let { revenueReport(it, window) }
    val selectedEntry: RevenueEntry? get() = snapshot?.entries?.find { it.order.id == entryId }
    val selectedSettlement: RevenueSettlement? get() = snapshot?.settlements?.find { it.id == settlementId }
    val visibleEntries: List<RevenueEntry> get() {
        val key = query.boxSearchKey()
        val bounds = window
        return snapshot?.entries.orEmpty().filter { entry ->
            val date = if (basis == RevenueBasis.PAYMENT) entry.paidAt else entry.order.collectedAtMillis
            date != null && date in bounds && (key.isBlank() ||
                (entry.order.reference + " " + entry.order.boxSnapshot?.name.orEmpty()).boxSearchKey().contains(key)) && when (filter) {
                RevenueFilter.ALL -> true
                RevenueFilter.ELIGIBLE -> entry.status == RevenueEntryStatus.ELIGIBLE
                RevenueFilter.WAITING -> entry.status == RevenueEntryStatus.WAITING_PICKUP
                RevenueFilter.HELD -> entry.status == RevenueEntryStatus.HELD || entry.status == RevenueEntryStatus.CANCELLED
                RevenueFilter.REFUNDS -> entry.refunded > 0 || entry.refundPending > 0
            }
        }.sortedByDescending { if (basis == RevenueBasis.PAYMENT) it.paidAt else it.order.collectedAtMillis }
    }
    val visibleSettlements: List<RevenueSettlement> get() = snapshot?.settlements.orEmpty().filter { when (settlementFilter) {
        SettlementFilter.ALL -> true
        SettlementFilter.AWAITING -> it.status == SettlementStatus.PENDING || it.status == SettlementStatus.READY
        SettlementFilter.PROCESSING -> it.status == SettlementStatus.PROCESSING
        SettlementFilter.PAID -> it.status == SettlementStatus.PAID
        SettlementFilter.FAILED -> it.status == SettlementStatus.FAILED
    } }
}
