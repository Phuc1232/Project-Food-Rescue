package com.foodrescue.app.feature.staff.presentation

import androidx.compose.runtime.*
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foodrescue.app.feature.staff.domain.StaffBoxConditionRepository
import com.foodrescue.app.feature.surprisebox.domain.*
import kotlinx.coroutines.*
import java.util.UUID

internal class StaffBoxConditionViewModel(private val savedState: SavedStateHandle, private val repository: StaffBoxConditionRepository,
    private val clock: () -> Long = System::currentTimeMillis) : ViewModel() {
    private var generation = 0
    private var refreshing = false
    var state by mutableStateOf(StaffBoxConditionUiState(draft = restore(), now = clock()))
        private set

    fun open(box: PackedBox) {
        if (state.busy) return
        generation++
        state = StaffBoxConditionUiState(draft = BoxInspectionDraft(UUID.randomUUID().toString(), box.id, box.inspections.size,
            noShowRequestId = box.noShow?.requestId), now = clock())
        persist()
    }
    suspend fun refresh() {
        if (state.busy || refreshing || state.draft.boxId.isBlank()) return
        refreshing = true
        val request = generation
        try {
            val workspace = withContext(Dispatchers.IO) { repository.load() }
            currentCoroutineContext().ensureActive()
            if (request != generation) return
            val box = workspace.boxes.firstOrNull { it.id == state.draft.boxId }
            val recovered = box?.inspections?.any { it.id == state.draft.id } == true
            state = state.copy(workspace = workspace, loading = false, verified = true, now = clock(), saved = recovered, error = null,
                confirm = state.confirm && box?.inspections?.size == state.draft.expectedRevision &&
                    box.noShow?.requestId == state.draft.noShowRequestId && box.canInspect() && workspace.access.canInspectBoxes())
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) {
            if (request == generation) state = state.copy(loading = false, verified = false, confirm = false,
                error = "Chưa cập nhật được tình trạng hộp. Tải lại trước khi tiếp tục.")
        } finally { refreshing = false }
    }
    fun reload() { viewModelScope.launch { refresh() } }
    fun edit(draft: BoxInspectionDraft) {
        if (!state.canEdit) return
        state = state.copy(draft = draft.copy(id = state.draft.id, boxId = state.draft.boxId, expectedRevision = state.draft.expectedRevision,
            noShowRequestId = state.draft.noShowRequestId, note = draft.note.take(500)), confirm = false, issues = emptySet())
        persist()
    }
    fun reviewLatest() {
        if (!state.canEdit) return
        state = state.copy(draft = state.draft.copy(id = UUID.randomUUID().toString(), expectedRevision = checkNotNull(state.box).inspections.size,
            noShowRequestId = state.box?.noShow?.requestId,
            foodChecked = false, packagingChecked = false, storageChecked = false, allergensChecked = false), issues = emptySet(), confirm = false)
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
        if (state.validation.isNotEmpty()) { state = state.copy(issues = state.validation, confirm = false); return }
        val request = ++generation
        val draft = state.draft
        state = state.copy(busy = true, error = null)
        try {
            // The short local commit settles; a stable ID recovers a lost receipt without a second inspection.
            val box = withContext(NonCancellable + Dispatchers.IO) { repository.inspect(draft) }
            if (request == generation) state = state.copy(workspace = state.workspace.copy(boxes = state.workspace.boxes.filterNot { it.id == box.id } + box), saved = true)
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (invalid: InspectionException) {
            if (request == generation) state = state.copy(issues = invalid.issues, verified = false,
                error = "Tình trạng hộp hoặc quyền kiểm tra đã thay đổi. Tải lại để xem thông tin mới.")
        } catch (_: Exception) {
            if (request == generation) state = state.copy(verified = false, error = "Chưa xác nhận được kết quả lưu. Tải lại để kiểm tra lịch sử.")
        } finally { if (request == generation) state = state.copy(busy = false, confirm = false) }
    }
    fun close() { if (!state.busy) clear() }
    fun clear() { generation++; savedState.remove<ArrayList<String>>("inspection.draft"); state = StaffBoxConditionUiState(now = clock()) }
    private fun persist() = with(state.draft) {
        savedState["inspection.draft"] = arrayListOf(id, boxId, expectedRevision.toString(), result?.name.orEmpty(), reason?.name.orEmpty(), note,
            foodChecked.toString(), packagingChecked.toString(), storageChecked.toString(), allergensChecked.toString(), noShowRequestId.orEmpty())
    }
    private fun restore(): BoxInspectionDraft {
        val values = savedState.get<ArrayList<String>>("inspection.draft") ?: return BoxInspectionDraft()
        if (values.size !in 10..11) return BoxInspectionDraft()
        return BoxInspectionDraft(values[0], values[1], values[2].toIntOrNull() ?: 0,
            InspectionResult.entries.firstOrNull { it.name == values[3] }, InspectionReason.entries.firstOrNull { it.name == values[4] }, values[5],
            values[6].toBoolean(), values[7].toBoolean(), values[8].toBoolean(), values[9].toBoolean(), values.getOrNull(10)?.takeIf { it.isNotBlank() })
    }
}
