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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.util.UUID

internal class StoreSupervisionViewModel(private val saved: SavedStateHandle,
    private val repository: StoreSupervisionRepository) : ViewModel() {
    var state by mutableStateOf(StoreSupervisionState(query = saved["supervision.query"] ?: "",
        selectedKey = saved["supervision.selected"],
        filter = SupervisionFilter.entries.find { it.name == saved.get<String>("supervision.filter") } ?: SupervisionFilter.ALL,
        section = SupervisionSection.entries.find { it.name == saved.get<String>("supervision.section") } ?: SupervisionSection.OVERVIEW))
        private set
    private var work: Job? = null

    fun refresh() {
        if (state.busy || state.draft != null) return
        state = state.copy(busy = true, error = null)
        work = viewModelScope.launch {
            try {
                val stores = repository.stores()
                val key = state.selectedKey
                val detail = key?.let { repository.detail(it) }
                ensureActive()
                state = state.copy(stores = stores, detail = detail)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { state = state.copy(error = "Chưa tải được dữ liệu giám sát. Vui lòng thử lại.")
            } finally { state = state.copy(busy = false, loading = false) }
        }
    }
    fun search(value: String) { state = state.copy(query = value.take(150)); saved["supervision.query"] = state.query }
    fun filter(value: SupervisionFilter) { state = state.copy(filter = value); saved["supervision.filter"] = value.name }
    fun section(value: SupervisionSection) { state = state.copy(section = value); saved["supervision.section"] = value.name }
    fun open(key: String) {
        if (state.busy) return
        state = state.copy(selectedKey = key, detail = null, boxId = null, menuId = null, section = SupervisionSection.OVERVIEW)
        saved["supervision.selected"] = key; saved["supervision.section"] = state.section.name
        refresh()
    }
    fun openBox(id: String) { state = state.copy(boxId = id) }
    fun openMenu(id: String) { state = state.copy(menuId = id) }
    fun back() {
        if (state.busy) return
        when {
            state.draft != null -> state = state.copy(draft = null, confirming = false, attempted = false)
            state.boxId != null || state.menuId != null -> state = state.copy(boxId = null, menuId = null)
            else -> { state = state.copy(selectedKey = null, detail = null, error = null); saved.remove<String>("supervision.selected") }
        }
    }
    fun prepare(action: StoreRestrictionAction, batchId: String? = null) {
        val detail = state.detail ?: return
        if (state.busy || state.error != null) return
        val batch = batchId?.let { id -> detail.catalog.batches.find { it.id == id } ?: return }
        state = state.copy(draft = StoreRestrictionDraft(UUID.randomUUID().toString(), detail.entry.store.revision, action, batch),
            attempted = false, confirming = false, error = null)
    }
    fun note(value: String) {
        if (!state.busy) state = state.copy(draft = state.draft?.copy(note = value.take(500)), attempted = false, confirming = false)
    }
    fun review() { if (!state.busy) state = state.copy(attempted = true, confirming = state.draft?.let { validRestrictionNote(it.note) } == true) }
    fun dismiss() { if (!state.busy) state = state.copy(confirming = false) }
    fun confirm() {
        val key = state.selectedKey ?: return
        val draft = state.draft ?: return
        if (state.busy || !state.confirming || !validRestrictionNote(draft.note)) return
        state = state.copy(busy = true, error = null)
        work = viewModelScope.launch {
            try {
                val entry = repository.decide(key, draft)
                ensureActive()
                state = state.copy(stores = state.stores.map { if (it.accountKey == key) entry else it },
                    detail = state.detail?.copy(entry = entry), draft = null, attempted = false, confirming = false,
                    notice = "Đã lưu quyết định giám sát")
                try {
                    val updated = repository.detail(key)
                    ensureActive(); state = state.copy(detail = updated)
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (_: Exception) { state = state.copy(error = "Quyết định đã lưu. Tải lại để cập nhật dữ liệu đối chiếu.") }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: StaleStoreSupervision) {
                state = state.copy(draft = null, confirming = false,
                    error = "Dữ liệu đã thay đổi. Tải lại và kiểm tra trước khi ra quyết định mới.")
            } catch (_: Exception) {
                state = state.copy(confirming = false, error = "Chưa lưu được quyết định. Lý do của bạn vẫn được giữ lại.")
            } finally { state = state.copy(busy = false) }
        }
    }
    fun consumeNotice() { state = state.copy(notice = null) }
    fun clear() {
        work?.cancel(); work = null
        listOf("query", "selected", "filter", "section").forEach { saved.remove<String>("supervision.$it") }
        state = StoreSupervisionState()
    }
}
