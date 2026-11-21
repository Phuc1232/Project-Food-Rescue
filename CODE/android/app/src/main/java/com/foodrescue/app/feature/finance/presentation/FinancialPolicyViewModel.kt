package com.foodrescue.app.feature.finance.presentation

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foodrescue.app.feature.finance.domain.*
import kotlinx.coroutines.*

internal class FinancialPolicyViewModel(private val repository: FinancialPolicyRepository,
    private val clock: () -> Long = System::currentTimeMillis) : ViewModel() {
    var state by mutableStateOf(FinancialPolicyState(now = clock()))
        private set
    private var work: Job? = null
    private var generation = 0
    fun refresh() {
        if (state.busy || state.draft != null) return
        run { val book = repository.load(); ensureActive(); state = state.copy(book = book, now = clock()) }
    }
    fun edit() {
        if (state.busy || state.error != null || state.book == null || state.scheduled != null) return
        state = state.copy(draft = PolicyDraft.from(state.current), confirming = false, attempted = false)
    }
    fun change(draft: PolicyDraft) { if (!state.busy) state = state.copy(draft = draft, confirming = false) }
    fun back() { if (!state.busy) state = state.copy(draft = null, confirming = false, attempted = false, error = null) }
    fun review() {
        if (state.busy) return
        state = state.copy(now = clock(), attempted = true)
        state = state.copy(confirming = state.valid)
    }
    fun dismiss() { if (!state.busy) state = state.copy(confirming = false) }
    fun confirm() {
        val draft = state.draft ?: return; val book = state.book ?: return
        if (state.busy || !state.confirming) return
        val now = clock(); val effective = if (draft.scheduled) draft.effectiveAt(now) ?: return else 0L
        val terms = draft.terms() ?: return
        run {
            val updated = repository.publish(book.revision, terms, effective, draft.reason)
            ensureActive()
            state = state.copy(book = updated, now = clock(), draft = null, confirming = false, attempted = false,
                notice = "Đã công bố chính sách phiên bản ${updated.revision}")
        }
    }
    private fun run(block: suspend CoroutineScope.() -> Unit) {
        val request = ++generation; state = state.copy(busy = true, error = null)
        work = viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: StaleFinanceData) { if (request == generation) state = state.copy(draft = null, confirming = false,
                error = "Chính sách đã có phiên bản mới. Tải lại trước khi chỉnh sửa.") }
            catch (_: Exception) { if (request == generation) state = state.copy(confirming = false,
                error = "Chưa lưu được chính sách. Kiểm tra thời điểm hiệu lực và tải lại dữ liệu.") }
            finally { if (request == generation) state = state.copy(busy = false) }
        }
    }
    fun consumeNotice() { state = state.copy(notice = null) }
    fun clear() { generation++; work?.cancel(); work = null; state = FinancialPolicyState(now = clock()) }
}
