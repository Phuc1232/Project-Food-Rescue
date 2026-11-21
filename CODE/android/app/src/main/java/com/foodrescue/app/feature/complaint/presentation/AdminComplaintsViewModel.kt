package com.foodrescue.app.feature.complaint.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foodrescue.app.feature.complaint.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class AdminComplaintsViewModel(private val saved: SavedStateHandle,
    private val repository: ComplaintReviewRepository, private val clock: () -> Long = System::currentTimeMillis) : ViewModel() {
    var state by mutableStateOf(AdminComplaintsState(query = saved["review.query"] ?: "",
        filter = AdminComplaintFilter.entries.find { it.name == saved.get<String>("review.filter") } ?: AdminComplaintFilter.OPEN,
        selectedKey = saved["review.selected"], now = clock()))
        private set
    private var work: Job? = null
    private var generation = 0
    private var expected: ComplaintReviewCase? = null

    fun refresh() {
        if (state.busy || state.decision != null) return
        val owner = generation
        state = state.copy(busy = true, error = null)
        work = viewModelScope.launch {
            try {
                val cases = repository.load()
                if (owner == generation) state = state.copy(cases = cases, loading = false, now = clock(),
                    error = if (state.selectedKey != null && cases.none { it.key == state.selectedKey }) "Không tìm thấy khiếu nại đã chọn." else null)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { if (owner == generation) state = state.copy(loading = false, error = "Không tải được khiếu nại. Vui lòng thử lại.")
            } finally { if (owner == generation) state = state.copy(busy = false) }
        }
    }
    fun search(value: String) { state = state.copy(query = value.take(150)); saved["review.query"] = state.query }
    fun filter(value: AdminComplaintFilter) { state = state.copy(filter = value); saved["review.filter"] = value.name }
    fun open(key: String) {
        if (state.busy) return
        state = state.copy(selectedKey = key, error = null); saved["review.selected"] = key
        refresh()
    }
    fun back() {
        if (state.busy) return
        if (state.decision != null) { expected = null; state = state.copy(decision = null, confirming = false, attempted = false, error = null) }
        else { state = state.copy(selectedKey = null, error = null); saved.remove<String>("review.selected") }
    }
    fun receive() {
        val row = state.selected ?: return
        if (row.complaint.status != ComplaintStatus.PENDING || state.busy || state.error != null) return
        save(row, ComplaintReviewAction.RECEIVED, "", 0)
    }
    fun decide(action: ComplaintReviewAction) {
        val row = state.selected ?: return
        if (state.busy || state.error != null || row.complaint.status != ComplaintStatus.IN_REVIEW || action == ComplaintReviewAction.RECEIVED) return
        if (action == ComplaintReviewAction.REFUND_REQUESTED && row.refundableAmount <= 0) return
        expected = row
        state = state.copy(decision = action, note = if (state.decision == null) "" else state.note,
            amount = if (state.decision == null) "" else state.amount, attempted = false, error = null)
    }
    fun note(value: String) { if (!state.busy) state = state.copy(note = value.take(MAX_REVIEW_RESPONSE), attempted = false) }
    fun amount(value: String) { if (!state.busy) state = state.copy(amount = value.filter { it in '0'..'9' }.take(15), attempted = false) }
    fun prepareConfirmation() {
        if (state.busy || state.decision == null) return
        state = state.copy(attempted = true)
        if (state.noteError == null && state.amountError == null) state = state.copy(confirming = true)
    }
    fun dismissConfirmation() { if (!state.busy) state = state.copy(confirming = false) }
    fun confirm() {
        val row = expected ?: return
        val action = state.decision ?: return
        if (!state.confirming || state.busy || !validComplaintResponse(state.note) || state.amountError != null) return
        save(row, action, state.note, if (action == ComplaintReviewAction.REFUND_REQUESTED) state.amount.toLongOrNull() ?: 0 else 0)
    }
    private fun save(row: ComplaintReviewCase, action: ComplaintReviewAction, note: String, amount: Long) {
        val owner = generation
        state = state.copy(busy = true, error = null)
        work = viewModelScope.launch {
            try {
                val result = repository.review(row, action, note, amount)
                if (owner == generation) {
                    expected = null
                    state = state.copy(cases = state.cases.map { if (it.key == result.key) result else it },
                        decision = null, confirming = false, attempted = false, note = "", amount = "", now = clock(),
                        notice = if (action == ComplaintReviewAction.RECEIVED) "Đã tiếp nhận khiếu nại" else "Đã lưu kết quả xử lý")
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                if (owner == generation) {
                    val stale = error is StaleComplaintReview || error is ChangedComplaintOrder
                    state = state.copy(confirming = false, decision = if (stale) null else state.decision,
                        error = if (stale) "Khiếu nại hoặc đơn hàng đã thay đổi. Tải lại để đối chiếu trước khi xử lý."
                            else "Chưa lưu được quyết định. Phản hồi vẫn được giữ lại, bạn có thể thử lại.")
                    if (stale) expected = null
                }
            } finally { if (owner == generation) state = state.copy(busy = false) }
        }
    }
    fun consumeNotice() { state = state.copy(notice = null) }
    fun clear() {
        generation++; work?.cancel(); work = null; expected = null
        saved.remove<String>("review.query"); saved.remove<String>("review.filter"); saved.remove<String>("review.selected")
        state = AdminComplaintsState(now = clock())
    }
}
