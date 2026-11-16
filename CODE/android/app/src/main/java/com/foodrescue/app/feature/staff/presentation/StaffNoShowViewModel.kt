package com.foodrescue.app.feature.staff.presentation

import androidx.compose.runtime.*
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.staff.domain.*
import com.foodrescue.app.feature.surprisebox.domain.PackedBoxNoShow
import kotlinx.coroutines.*
import java.util.UUID

internal class StaffNoShowViewModel(private val saved: SavedStateHandle, private val repository: StaffNoShowRepository,
    private val clock: () -> Long = System::currentTimeMillis) : ViewModel() {
    private var generation = 0
    private var refreshing = false
    var state by mutableStateOf(StaffNoShowUiState(draft = restore(), now = clock()))
        private set

    fun open(orderId: String) {
        if (state.busy) return
        generation++
        state = StaffNoShowUiState(draft = NoShowDraft(UUID.randomUUID().toString(), orderId), now = clock())
        persist()
    }
    suspend fun refresh() {
        if (state.busy || refreshing || state.draft.orderId.isBlank()) return
        refreshing = true
        val request = generation
        try {
            val workspace = withContext(Dispatchers.IO) { repository.load() }
            currentCoroutineContext().ensureActive()
            if (request != generation) return
            val visible = if (workspace.access?.allowed == true && EmployeePermission.VIEW_ORDERS in workspace.access.permissions)
                workspace else HandoverWorkspace(workspace.access)
            state = state.copy(workspace = visible, loading = false, current = true, error = null, now = clock())
            state = state.copy(confirm = state.confirm && state.canEdit && state.validation.isEmpty())
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) {
            if (request == generation) state = state.copy(loading = false, current = false, confirm = false,
                error = "Chưa cập nhật được đơn hàng. Tải lại trước khi xác nhận.")
        } finally { refreshing = false }
    }
    fun reload() { viewModelScope.launch { refresh() } }
    fun edit(value: NoShowDraft) {
        if (!state.canEdit) return
        state = state.copy(draft = value.copy(requestId = state.draft.requestId, orderId = state.draft.orderId, note = value.note.take(500)),
            confirm = false, issues = emptySet())
        persist()
    }
    fun requestConfirmation() {
        if (!state.canEdit) return
        state = state.copy(now = clock())
        state = state.copy(issues = state.validation, confirm = state.validation.isEmpty())
    }
    fun dismissConfirmation() { if (!state.busy) state = state.copy(confirm = false) }
    fun finish() { viewModelScope.launch { submit() } }
    suspend fun submit() {
        if (!state.canEdit || !state.confirm) return
        state = state.copy(now = clock())
        if (state.validation.isNotEmpty()) { state = state.copy(confirm = false, issues = state.validation); return }
        val request = ++generation
        val draft = state.draft
        state = state.copy(busy = true, error = null)
        try {
            val order = withContext(NonCancellable + Dispatchers.IO) { repository.confirm(draft) }
            if (request != generation) return
            val receipt = checkNotNull(order.noShow)
            val boxes = state.workspace.boxes.map { box -> if (box.id in receipt.boxIds)
                box.copy(noShow = PackedBoxNoShow(order.id, order.reference, receipt.requestId, receipt.atMillis)) else box }
            state = state.copy(workspace = state.workspace.copy(orders = state.workspace.orders.map { if (it.id == order.id) order else it }, boxes = boxes), issues = emptySet())
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (invalid: NoShowException) {
            if (request == generation) state = state.copy(current = false, issues = invalid.issues,
                error = "Đơn hàng, hộp hoặc quyền xử lý đã thay đổi. Tải lại để kiểm tra.")
        } catch (_: Exception) {
            if (request == generation) state = state.copy(current = false, error = "Chưa xác nhận được kết quả lưu. Tải lại để đối chiếu.")
        } finally { if (request == generation) state = state.copy(busy = false, confirm = false) }
    }
    fun close() { if (!state.busy) clear() }
    fun clear() {
        generation++; saved.keys().filter { it.startsWith("noShow.") }.forEach { saved.remove<Any>(it) }
        state = StaffNoShowUiState(now = clock())
    }
    private fun persist() {
        saved["noShow.order"] = state.draft.orderId; saved["noShow.request"] = state.draft.requestId
        saved["noShow.note"] = state.draft.note; saved["noShow.boxes"] = ArrayList(state.draft.boxIds)
    }
    private fun restore() = NoShowDraft(saved["noShow.request"] ?: "", saved["noShow.order"] ?: "",
        saved.get<ArrayList<String>>("noShow.boxes")?.toList().orEmpty(), saved["noShow.note"] ?: "")
}
