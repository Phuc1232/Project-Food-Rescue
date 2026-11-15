package com.foodrescue.app.feature.finance.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foodrescue.app.feature.finance.domain.*
import kotlinx.coroutines.*

internal class RevenueViewModel(private val saved: SavedStateHandle, private val repository: RevenueRepository,
    private val clock: () -> Long = System::currentTimeMillis) : ViewModel() {
    private var generation = 0
    private var reloadJob: Job? = null
    var state by mutableStateOf(RevenueUiState(now = clock(),
        period = enum<RevenuePeriod>(saved["revenue.period"]) ?: RevenuePeriod.WEEK,
        offset = (saved.get<Int>("revenue.offset") ?: 0).coerceIn(-1200, 0),
        page = enum<RevenuePage>(saved["revenue.page"]) ?: RevenuePage.OVERVIEW,
        basis = enum<RevenueBasis>(saved["revenue.basis"]) ?: RevenueBasis.PAYMENT,
        filter = enum<RevenueFilter>(saved["revenue.filter"]) ?: RevenueFilter.ALL,
        settlementFilter = enum<SettlementFilter>(saved["revenue.settlementFilter"]) ?: SettlementFilter.ALL,
        query = saved["revenue.query"] ?: "", entryId = saved["revenue.entry"], settlementId = saved["revenue.settlement"],
        entryFromSettlement = saved["revenue.entryFromSettlement"] ?: false,
        settlementFromEntry = saved["revenue.settlementFromEntry"] ?: false))
        private set

    suspend fun refresh() {
        val request = ++generation
        try {
            val result = repository.load()
            currentCoroutineContext().ensureActive()
            if (request == generation) state = state.copy(snapshot = result, loading = false, error = null, now = clock())
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) {
            if (request == generation) state = state.copy(snapshot = null, loading = false,
                error = "Chưa tải được số liệu. Vui lòng thử lại.", now = clock())
        }
    }
    fun reload() { if (reloadJob?.isActive != true) reloadJob = viewModelScope.launch { refresh() } }
    fun period(value: RevenuePeriod) { state = state.copy(period = value, offset = 0); persist() }
    fun movePeriod(step: Int) { state = state.copy(offset = (state.offset + step.coerceIn(-1, 1)).coerceIn(-1200, 0)); persist() }
    fun currentPeriod() { state = state.copy(offset = 0); persist() }
    fun search(value: String) { state = state.copy(query = value.take(100)); persist() }
    fun filter(value: RevenueFilter) { state = state.copy(filter = value); persist() }
    fun basis(value: RevenueBasis) { state = state.copy(basis = value); persist() }
    fun settlementFilter(value: SettlementFilter) { state = state.copy(settlementFilter = value); persist() }
    fun resetFilters() { state = state.copy(query = "", filter = RevenueFilter.ALL); persist() }
    fun transactions(basis: RevenueBasis = RevenueBasis.PAYMENT) {
        state = state.copy(page = RevenuePage.TRANSACTIONS, basis = basis, entryFromSettlement = false, settlementFromEntry = false); persist()
    }
    fun settlements() { state = state.copy(page = RevenuePage.SETTLEMENTS, entryFromSettlement = false, settlementFromEntry = false); persist() }
    fun openEntry(id: String) {
        if (state.snapshot?.entries?.none { it.order.id == id } != false) return
        state = state.copy(page = RevenuePage.TRANSACTION, entryId = id,
            entryFromSettlement = state.page == RevenuePage.SETTLEMENT && !state.settlementFromEntry,
            settlementFromEntry = false); persist()
    }
    fun openSettlement(id: String) {
        if (state.snapshot?.settlements?.none { it.id == id } != false) return
        state = state.copy(page = RevenuePage.SETTLEMENT, settlementId = id,
            settlementFromEntry = state.page == RevenuePage.TRANSACTION && !state.entryFromSettlement,
            entryFromSettlement = false); persist()
    }
    fun back() {
        state = when (state.page) {
            RevenuePage.TRANSACTION -> state.copy(page = if (state.entryFromSettlement) RevenuePage.SETTLEMENT else RevenuePage.TRANSACTIONS, entryFromSettlement = false)
            RevenuePage.SETTLEMENT -> state.copy(page = if (state.settlementFromEntry) RevenuePage.TRANSACTION else RevenuePage.SETTLEMENTS, settlementFromEntry = false)
            else -> state.copy(page = RevenuePage.OVERVIEW)
        }; persist()
    }
    fun clear() { generation++; reloadJob?.cancel(); reloadJob = null
        saved.keys().filter { it.startsWith("revenue.") }.forEach { saved.remove<Any>(it) }; state = RevenueUiState(now = clock()) }
    private fun persist() {
        saved["revenue.period"] = state.period.name; saved["revenue.offset"] = state.offset; saved["revenue.page"] = state.page.name
        saved["revenue.basis"] = state.basis.name; saved["revenue.filter"] = state.filter.name; saved["revenue.query"] = state.query
        saved["revenue.settlementFilter"] = state.settlementFilter.name; saved["revenue.entry"] = state.entryId
        saved["revenue.settlement"] = state.settlementId; saved["revenue.entryFromSettlement"] = state.entryFromSettlement
        saved["revenue.settlementFromEntry"] = state.settlementFromEntry
    }
    private inline fun <reified T : Enum<T>> enum(value: String?): T? = value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }
}
