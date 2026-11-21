package com.foodrescue.app.feature.refund.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foodrescue.app.feature.refund.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class AdminRefundsViewModel(private val saved: SavedStateHandle, private val repository: AdminRefundRepository) : ViewModel() {
    var state by mutableStateOf(AdminRefundsState(query = saved["refund.query"] ?: "",
        filter = RefundFilter.entries.find { it.name == saved.get<String>("refund.filter") } ?: RefundFilter.ACTION,
        selectedKey = saved["refund.selected"]))
        private set
    private var work: Job? = null
    private var generation = 0
    private var expected: RefundCase? = null
    fun refresh() {
        if (state.busy || state.approve != null) return
        val owner = generation
        state = state.copy(busy = true)
        work = viewModelScope.launch {
            try {
                val cases = repository.load()
                if (owner == generation) state = state.copy(cases = cases, loading = false,
                    error = if (state.selectedKey != null && cases.none { it.key == state.selectedKey }) "Không tìm thấy yêu cầu hoàn tiền." else null)
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { if (owner == generation) state = state.copy(loading = false, error = "Chưa tải được dữ liệu hoàn tiền. Vui lòng tải lại.")
            } finally { if (owner == generation) state = state.copy(busy = false) }
        }
    }
    fun search(value: String) { state = state.copy(query = value.take(150)); saved["refund.query"] = state.query }
    fun filter(value: RefundFilter) { state = state.copy(filter = value); saved["refund.filter"] = value.name }
    fun open(key: String) {
        if (state.busy) return
        state = state.copy(selectedKey = key, error = null); saved["refund.selected"] = key; refresh()
    }
    fun back() {
        if (state.busy) return
        if (state.approve != null) { expected = null; state = state.copy(approve = null, confirming = false, attempted = false) }
        else { state = state.copy(selectedKey = null, error = null); saved.remove<String>("refund.selected") }
    }
    fun prepare(approve: Boolean) {
        val row = state.selected ?: return
        if (state.busy || state.error != null || (approve && !row.canProcess)) return
        if (!approve && (row.source != RefundSource.STORE_CANCELLATION || row.status != AdminRefundStatus.PENDING)) return
        expected = row
        state = state.copy(approve = approve, note = "", attempted = false, error = null)
    }
    fun note(value: String) { if (!state.busy) state = state.copy(note = value.take(500), attempted = false) }
    fun review() {
        if (state.busy || state.approve == null) return
        state = state.copy(attempted = true, confirming = validRefundNote(state.note))
    }
    fun dismiss() { if (!state.busy) state = state.copy(confirming = false) }
    fun confirm() {
        val row = expected ?: return
        val approve = state.approve ?: return
        if (state.busy || !state.confirming || !validRefundNote(state.note)) return
        val owner = generation
        val note = state.note
        state = state.copy(busy = true, error = null)
        work = viewModelScope.launch {
            try {
                val result = repository.decide(row, approve, note)
                if (owner == generation) {
                    expected = null
                    state = state.copy(cases = state.cases.map { if (it.key == result.key) result else it },
                        approve = null, note = "", attempted = false, confirming = false,
                        notice = if (approve) "Đã gửi yêu cầu hoàn tiền" else "Đã từ chối yêu cầu hủy đơn")
                }
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                if (owner == generation) {
                    val stale = error is StaleRefund
                    state = state.copy(confirming = false, approve = if (stale) null else state.approve,
                        error = if (stale) "Đơn hàng hoặc yêu cầu đã thay đổi. Tải lại và kiểm tra trước khi xác nhận."
                        else "Chưa lưu được kết quả. Lý do vẫn được giữ lại; bạn có thể thử lại.")
                    if (stale) expected = null
                }
            } finally { if (owner == generation) state = state.copy(busy = false) }
        }
    }
    fun consumeNotice() { state = state.copy(notice = null) }
    fun clear() {
        generation++; work?.cancel(); expected = null
        listOf("refund.query", "refund.filter", "refund.selected").forEach { saved.remove<String>(it) }
        state = AdminRefundsState()
    }
}
