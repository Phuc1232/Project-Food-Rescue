package com.foodrescue.app.feature.staff.presentation

import androidx.compose.runtime.*
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.staff.domain.*
import com.foodrescue.app.feature.surprisebox.domain.PackedBoxHandover
import kotlinx.coroutines.*
import java.util.UUID

internal class StaffHandoverViewModel(private val saved: SavedStateHandle, private val repository: StaffHandoverRepository,
    private val clock: () -> Long = System::currentTimeMillis) : ViewModel() {
    private var generation = 0
    private var refreshing = false
    var state by mutableStateOf(StaffHandoverUiState(draft = HandoverDraft(saved["handover.request"] ?: "", saved["handover.order"] ?: ""), now = clock()))
        private set
    suspend fun refresh() {
        if (state.busy || refreshing) return
        refreshing = true
        val request = generation
        try {
            val workspace = repository.load()
            currentCoroutineContext().ensureActive()
            if (request != generation) return
            state = state.copy(workspace = workspace, loading = false, current = true, error = null, now = clock())
            if (!state.canRead) { close(); state = state.copy(workspace = HandoverWorkspace(workspace.access)) }
            else if (!state.canWrite || state.order == null || state.order?.handoverAvailability(workspace.access, state.now)?.isNotEmpty() == true ||
                (state.codeVerified && HandoverIssue.CODE in state.validation)) {
                state = state.copy(codeVerified = false, confirm = false, draft = state.draft.copy(code = "", boxIds = emptyList()))
            } else {
                val eligible = state.availableBoxes.map { it.id }.toSet()
                val selected = state.draft.boxIds.filter { it in eligible }.take(state.order?.quantity ?: 0)
                if (selected != state.draft.boxIds) state = state.copy(draft = state.draft.copy(boxIds = selected), confirm = false,
                    message = "Hộp đã chọn không còn khả dụng. Chọn lại hộp để tiếp tục.")
            }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) {
            if (request == generation) state = state.copy(loading = false, current = false, confirm = false,
                error = "Chưa cập nhật được đơn hàng. Tải lại trước khi bàn giao.")
        } finally { refreshing = false }
    }
    fun reload() { viewModelScope.launch { refresh() } }
    fun search(value: String) { if (!state.busy) state = state.copy(query = value.take(100)) }
    fun filter(value: HandoverFilter) { if (!state.busy) state = state.copy(filter = value) }
    fun open(id: String) {
        if (state.busy || !state.canRead || state.workspace.orders.none { it.id == id }) return
        val draft = HandoverDraft(UUID.randomUUID().toString(), id)
        saved["handover.order"] = id; saved["handover.request"] = draft.requestId
        state = state.copy(draft = draft, codeVerified = false, confirm = false, failureForm = false, issues = emptySet(), message = null)
    }
    fun close() {
        if (state.busy) return
        saved.remove<String>("handover.order"); saved.remove<String>("handover.request")
        state = state.copy(draft = HandoverDraft("", ""), codeVerified = false, confirm = false, failureForm = false,
            failure = HandoverFailureDraft("", ""), issues = emptySet(), message = null)
    }
    fun editCode(value: String) {
        if (!state.canWrite) return
        state = state.copy(draft = state.draft.copy(code = value.take(64), boxIds = emptyList()), codeVerified = false, confirm = false, issues = emptySet())
    }
    fun verifyCode() {
        if (!state.canWrite) return
        state = state.copy(now = clock())
        val issues = state.validation - setOf(HandoverIssue.QUANTITY, HandoverIssue.BOX)
        state = state.copy(codeVerified = issues.isEmpty(), issues = issues)
    }
    fun toggleBox(id: String) {
        if (!state.canWrite || !state.codeVerified || state.availableBoxes.none { it.id == id }) return
        val selected = state.draft.boxIds
        val next = if (id in selected) selected - id else if (selected.size < (state.order?.quantity ?: 0)) selected + id else selected
        state = state.copy(draft = state.draft.copy(boxIds = next), confirm = false, issues = emptySet())
    }
    fun requestConfirmation() {
        state = state.copy(now = clock())
        if (state.ready) state = state.copy(confirm = true) else state = state.copy(issues = state.validation)
    }
    fun dismissConfirmation() { if (!state.busy) state = state.copy(confirm = false) }
    fun finish() { viewModelScope.launch { submit() } }
    suspend fun submit() {
        state = state.copy(now = clock())
        if (!state.confirm || !state.ready) { state = state.copy(confirm = false, issues = state.validation); return }
        val draft = state.draft
        mutate { repository.complete(draft) }
    }
    fun showFailure() {
        if (!state.canWrite || state.order?.handoverAvailability(state.workspace.access, clock())?.minus(HandoverIssue.ENDED)?.isEmpty() != true) return
        state = state.copy(failureForm = true, failure = HandoverFailureDraft(UUID.randomUUID().toString(), state.draft.orderId), issues = emptySet(), message = null)
    }
    fun editFailure(value: HandoverFailureDraft) { if (state.canWrite) state = state.copy(failure = state.failure.copy(reason = value.reason, note = value.note.take(500)), issues = emptySet()) }
    fun cancelFailure() { if (!state.busy) state = state.copy(failureForm = false, issues = emptySet()) }
    fun saveFailure() { viewModelScope.launch { submitFailure() } }
    suspend fun submitFailure() {
        if (!state.canWrite || !state.failureForm) return
        val failure = state.failure
        mutate { repository.recordFailure(failure) }
    }
    private suspend fun mutate(action: suspend () -> BoxReservation) {
        val request = ++generation
        state = state.copy(busy = true, error = null)
        try {
            val order = withContext(NonCancellable) { action() }
            if (request != generation) return
            val delivered = order.handover
            val boxes = state.workspace.boxes.map { box -> if (delivered != null && box.id in delivered.boxIds)
                box.copy(handover = PackedBoxHandover(order.id, order.reference, delivered.atMillis)) else box }
            state = state.copy(workspace = state.workspace.copy(orders = state.workspace.orders.map { if (it.id == order.id) order else it }, boxes = boxes),
                confirm = false, failureForm = false, codeVerified = false, draft = state.draft.copy(code = "", boxIds = emptyList()), issues = emptySet(),
                message = if (delivered == null) "Đã ghi nhận. Chủ cửa hàng có thể xem để xử lý." else null)
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (invalid: HandoverException) {
            if (request == generation) state = state.copy(issues = invalid.issues, confirm = false,
                current = invalid.issues.all { it == HandoverIssue.NOTE || it == HandoverIssue.REASON },
                error = if (invalid.issues.any { it != HandoverIssue.NOTE && it != HandoverIssue.REASON }) "Thông tin đã thay đổi. Tải lại để kiểm tra." else null)
        } catch (_: Exception) {
            if (request == generation) state = state.copy(current = false, confirm = false,
                error = "Chưa xác nhận được kết quả lưu. Tải lại để kiểm tra trước khi thao tác tiếp.")
        } finally { if (request == generation) state = state.copy(busy = false, now = clock()) }
    }
    fun clear() {
        generation++; saved.keys().filter { it.startsWith("handover.") }.forEach { saved.remove<Any>(it) }
        state = StaffHandoverUiState(now = clock())
    }
}
