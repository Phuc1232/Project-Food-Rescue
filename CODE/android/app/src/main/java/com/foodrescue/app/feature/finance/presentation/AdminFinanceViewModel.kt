package com.foodrescue.app.feature.finance.presentation

import androidx.compose.runtime.*
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foodrescue.app.feature.finance.domain.*
import kotlinx.coroutines.*

internal class AdminFinanceViewModel(private val saved: SavedStateHandle,
    private val repository: AdminSettlementRepository) : ViewModel() {
    var state by mutableStateOf(AdminFinanceState(query = saved["finance.query"] ?: "",
        selectedKey = saved["finance.selected"], filter = SettlementStatus.entries.find { it.name == saved.get<String>("finance.filter") }))
        private set
    private var work: Job? = null
    private var generation = 0
    fun refresh() {
        if (state.busy || state.decision != null) return
        run { val snapshot = repository.load(); currentCoroutineContext().ensureActive(); state = state.copy(snapshot = snapshot,
            selectedKey = state.selectedKey?.takeIf { key -> snapshot.settlements.any { it.key == key } }) }
    }
    fun search(value: String) { state = state.copy(query = value.take(100)); saved["finance.query"] = state.query }
    fun filter(value: SettlementStatus?) { state = state.copy(filter = value); saved["finance.filter"] = value?.name }
    fun open(key: String) {
        if (state.busy) return
        state = state.copy(selectedKey = key, error = null); saved["finance.selected"] = key; refresh()
    }
    fun back() {
        if (state.busy) return
        if (state.decision != null) state = state.copy(decision = null, confirming = false, attempted = false)
        else { state = state.copy(selectedKey = null, error = null); saved.remove<String>("finance.selected") }
    }
    fun prepare(action: SettlementAction) {
        if (state.busy || state.error != null || action !in state.selected?.actions.orEmpty()) return
        state = state.copy(decision = SettlementDecision(action), attempted = false, confirming = false)
    }
    fun reason(value: String) { if (!state.busy) state = state.copy(decision = state.decision?.copy(reason = value.take(500)), confirming = false) }
    fun reference(value: String) { if (!state.busy) state = state.copy(decision = state.decision?.copy(transferReference = value.take(80)), confirming = false) }
    fun review() { if (!state.busy) state = state.copy(attempted = true, confirming = state.decisionValid) }
    fun dismiss() { if (!state.busy) state = state.copy(confirming = false) }
    fun confirm() {
        val expected = state.selected ?: return; val decision = state.decision ?: return
        if (state.busy || !state.confirming || !state.decisionValid) return
        run {
            val updated = repository.decide(expected, decision)
            currentCoroutineContext().ensureActive()
            state = state.copy(decision = null, confirming = false, attempted = false,
                selectedKey = updated.key, snapshot = state.snapshot?.copy(settlements = state.snapshot!!.settlements.map {
                    if (it.key == expected.key) updated else it
                }), notice = "Đã lưu ${updated.record.status.label.lowercase()}")
            saved["finance.selected"] = updated.key
            // A committed receipt remains visible even if a subsequent refresh fails.
            val snapshot = repository.load(); currentCoroutineContext().ensureActive()
            state = state.copy(snapshot = snapshot)
        }
    }
    private fun run(block: suspend () -> Unit) {
        val request = ++generation; state = state.copy(busy = true, error = null)
        work = viewModelScope.launch {
            try { block(); ensureActive() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: StaleFinanceData) { if (request == generation) state = state.copy(decision = null, confirming = false,
                error = "Dữ liệu đối soát đã thay đổi. Tải lại và kiểm tra trước khi xác nhận.") }
            catch (_: Exception) { if (request == generation) state = state.copy(confirming = false,
                error = "Chưa hoàn tất thao tác. Tải lại để kiểm tra trạng thái trước khi tiếp tục.") }
            finally { if (request == generation) state = state.copy(busy = false) }
        }
    }
    fun consumeNotice() { state = state.copy(notice = null) }
    fun clear() {
        generation++; work?.cancel(); work = null
        listOf("query", "selected", "filter").forEach { saved.remove<String>("finance.$it") }
        state = AdminFinanceState()
    }
}
