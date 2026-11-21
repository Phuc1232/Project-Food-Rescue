package com.foodrescue.app.feature.store.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foodrescue.app.feature.store.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class StoreApplicationsViewModel(private val saved: SavedStateHandle,
    private val repository: StoreApplicationRepository) : ViewModel() {
    var state by mutableStateOf(StoreApplicationsState(query = saved["applications.query"] ?: "",
        filter = ApplicationFilter.entries.find { it.name == saved.get<String>("applications.filter") } ?: ApplicationFilter.PENDING,
        selectedKey = saved["applications.selected"]))
        private set
    private var work: Job? = null

    fun refresh() {
        if (state.busy || state.decision != null) return
        state = state.copy(busy = true, error = null)
        work = viewModelScope.launch {
            try { state = state.copy(applications = repository.applications(), loading = false) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { state = state.copy(error = "Không tải được hồ sơ. Vui lòng thử lại.") }
            finally { state = state.copy(busy = false) }
        }
    }

    fun search(value: String) { state = state.copy(query = value.take(150)); saved["applications.query"] = state.query }
    fun filter(value: ApplicationFilter) { state = state.copy(filter = value); saved["applications.filter"] = value.name }
    fun open(key: String) {
        if (state.busy) return
        state = state.copy(selectedKey = key, error = null)
        saved["applications.selected"] = key
    }
    fun back() {
        if (state.busy) return
        if (state.decision != null) cancelDecision() else {
            state = state.copy(selectedKey = null, error = null)
            saved.remove<String>("applications.selected")
        }
    }
    fun decide(action: StoreReviewAction) {
        val store = state.selected?.store ?: return
        if (state.busy || store.status != StoreReviewStatus.PENDING || action == StoreReviewAction.SUBMITTED) return
        state = state.copy(decision = action, decisionRevision = store.revision, note = "", attempted = false, error = null)
    }
    fun note(value: String) { if (!state.busy) state = state.copy(note = value.take(1000), attempted = false) }
    fun prepareConfirmation() {
        val action = state.decision ?: return
        if (state.busy) return
        state = state.copy(attempted = true, confirming = validReviewNote(action, state.note))
    }
    fun dismissConfirmation() { if (!state.busy) state = state.copy(confirming = false) }
    fun cancelDecision() {
        if (!state.busy) state = state.copy(decision = null, decisionRevision = null, confirming = false,
            note = "", attempted = false, error = null)
    }
    fun confirm() {
        val key = state.selectedKey ?: return
        val revision = state.decisionRevision ?: return
        val action = state.decision ?: return
        val note = state.note
        if (state.busy || !state.confirming || !validReviewNote(action, note)) return
        state = state.copy(busy = true, error = null)
        work = viewModelScope.launch {
            try {
                val updated = repository.review(key, revision, action, note)
                state = state.copy(applications = state.applications.map { if (it.accountKey == key) updated else it },
                    decision = null, decisionRevision = null, confirming = false, note = "", attempted = false,
                    notice = if (action == StoreReviewAction.APPROVED) "Đã duyệt cửa hàng" else "Đã gửi yêu cầu bổ sung")
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: StaleStoreApplication) {
                state = state.copy(decision = null, decisionRevision = null, confirming = false,
                    error = "Hồ sơ đã thay đổi. Tải lại để xem trạng thái mới trước khi xử lý.")
            } catch (_: Exception) {
                state = state.copy(confirming = false, error = "Chưa lưu được quyết định. Nội dung của bạn vẫn được giữ lại.")
            } finally { state = state.copy(busy = false) }
        }
    }
    fun consumeNotice() { state = state.copy(notice = null) }
    fun clear() {
        work?.cancel(); work = null
        saved.remove<String>("applications.query"); saved.remove<String>("applications.filter"); saved.remove<String>("applications.selected")
        state = StoreApplicationsState()
    }
}
