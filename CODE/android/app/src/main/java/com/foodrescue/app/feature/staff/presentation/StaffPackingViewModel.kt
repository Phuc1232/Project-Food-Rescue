package com.foodrescue.app.feature.staff.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foodrescue.app.feature.menu.domain.MenuStatus
import com.foodrescue.app.feature.menu.domain.MenuUnit
import com.foodrescue.app.feature.staff.domain.*
import com.foodrescue.app.feature.surprisebox.domain.*
import kotlinx.coroutines.*
import java.util.UUID

internal class StaffPackingViewModel(private val saved: SavedStateHandle, private val repository: StaffPackingRepository,
    private val clock: () -> Long = System::currentTimeMillis) : ViewModel() {
    private var generation = 0
    private var refreshing = false
    var state by mutableStateOf(StaffPackingUiState(page = enumValue<StaffPage>(saved["staff.page"]) ?: StaffPage.OVERVIEW,
        draft = restoreDraft(), step = (saved.get<Int>("staff.step") ?: 0).coerceIn(0, 2),
        selectedId = saved["staff.selected"], now = clock()))
        private set

    suspend fun refresh() {
        if (state.busy || refreshing) return
        refreshing = true
        val request = generation
        try {
            val workspace = repository.load()
            currentCoroutineContext().ensureActive()
            if (request != generation) return
            state = state.copy(workspace = workspace, loading = false, verified = true, now = clock(), error = null)
            if (!state.canPrepare) {
                clearDraft()
                if (workspace.access?.allowed != true || state.page == StaffPage.PACKING) navigate(StaffPage.OVERVIEW)
            } else if (state.draft.id.isNotEmpty()) {
                // Recover a commit whose receipt was lost when Android stopped the process.
                workspace.boxes.firstOrNull { it.id == state.draft.id }?.let { showCompleted(it) }
            }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) {
            if (request == generation) state = state.copy(loading = false, verified = false,
                error = "Chưa cập nhật được dữ liệu cửa hàng. Tải lại trước khi tiếp tục.")
        } finally { refreshing = false }
    }
    fun reload() { viewModelScope.launch { refresh() } }
    fun navigate(page: StaffPage) {
        if (state.busy) return
        state = state.copy(page = page, query = "", issues = emptySet())
        saved["staff.page"] = page.name
    }
    fun search(query: String) { state = state.copy(query = query.take(100)) }
    fun filterBoxes(condition: BoxCondition?) { state = state.copy(boxFilter = condition) }
    fun begin(batchId: String) {
        if (!state.canPrepare || state.busy) return
        if (state.draft.id.isNotEmpty()) { navigate(StaffPage.PACKING); return }
        val batch = state.workspace.batches.firstOrNull { it.id == batchId && it.canPackAt(clock()) } ?: return
        state = state.copy(draft = PackingDraft(UUID.randomUUID().toString(), batch.id), step = 0)
        persistDraft(); navigate(StaffPage.PACKING)
    }
    fun resume() { if (state.canPrepare && state.draft.id.isNotEmpty()) navigate(StaffPage.PACKING) }
    fun quantity(id: String, value: String) {
        if (!state.canPrepare || state.busy) return
        val item = state.workspace.menu.firstOrNull { it.id == id && it.status == MenuStatus.ACTIVE } ?: return
        val selected = state.draft.selections.filterNot { it.itemId == id }
        val lines = if (value.isBlank()) selected else selected + PackingSelection(id, value.take(12), item.price, item.unit, item.updatedAtMillis)
        state = state.copy(draft = state.draft.copy(selections = lines, foodChecked = false, packagingChecked = false, allergensChecked = false), issues = emptySet())
        persistDraft()
    }
    fun updateMenuSelections() {
        if (!state.canPrepare || state.busy) return
        val selected = state.draft.selections.mapNotNull { old -> state.workspace.menu.firstOrNull { it.id == old.itemId && it.status == MenuStatus.ACTIVE }?.let {
            old.copy(unitPrice = it.price, unit = it.unit, updatedAt = it.updatedAtMillis)
        } }
        state = state.copy(draft = state.draft.copy(selections = selected, foodChecked = false, packagingChecked = false, allergensChecked = false), step = 0, issues = emptySet())
        persistDraft()
    }
    fun safety(expiresAt: Long, storage: BoxStorage?, food: Boolean, packaging: Boolean, allergens: Boolean) {
        if (!state.canPrepare || state.busy) return
        state = state.copy(draft = state.draft.copy(expiresAt = expiresAt, storage = storage,
            foodChecked = food, packagingChecked = packaging, allergensChecked = allergens), issues = emptySet())
        persistDraft()
    }
    fun next() {
        if (!state.canPrepare || state.busy) return
        state = state.copy(now = clock())
        val issues = state.validation - if (state.step == 0) setOf(PackingIssue.EXPIRY, PackingIssue.STORAGE, PackingIssue.INSPECTION) else emptySet()
        state = if (issues.isNotEmpty()) state.copy(issues = issues) else state.copy(step = (state.step + 1).coerceAtMost(2), query = "", issues = emptySet())
        persistDraft()
    }
    fun back() {
        if (state.busy) return
        when {
            state.page == StaffPage.PACKING && state.step > 0 -> { state = state.copy(step = state.step - 1, issues = emptySet()); persistDraft() }
            state.page == StaffPage.DETAIL -> navigate(StaffPage.BOXES)
            else -> navigate(StaffPage.OVERVIEW)
        }
    }
    fun openBox(id: String) {
        if (state.busy) return
        state = state.copy(selectedId = id); saved["staff.selected"] = id; navigate(StaffPage.DETAIL)
    }
    fun requestDiscard() { if (!state.busy) state = state.copy(confirmDiscard = true) }
    fun keepDraft() { state = state.copy(confirmDiscard = false) }
    fun discard() { if (!state.busy) { clearDraft(); navigate(StaffPage.OVERVIEW) } }
    fun finish() { viewModelScope.launch { submit() } }
    suspend fun submit() {
        state = state.copy(now = clock())
        if (!state.canComplete) { state = state.copy(issues = state.validation); return }
        val request = ++generation
        state = state.copy(busy = true, error = null)
        try {
            // A local metadata commit must settle once started; its id is stable across retries.
            val box = withContext(NonCancellable) { repository.complete(state.draft) }
            if (request == generation) showCompleted(box)
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (invalid: PackingException) {
            if (request == generation) state = state.copy(issues = invalid.issues, verified = false,
                error = "Thông tin hoặc quyền đóng gói đã thay đổi. Tải lại để kiểm tra.")
        } catch (_: Exception) {
            if (request == generation) state = state.copy(error = "Chưa xác nhận được kết quả lưu. Tải lại để kiểm tra hộp vừa đóng gói.", verified = false)
        } finally { if (request == generation) state = state.copy(busy = false) }
    }
    private fun showCompleted(box: PackedBox) {
        state = state.copy(workspace = state.workspace.copy(boxes = state.workspace.boxes.filterNot { it.id == box.id } + box),
            selectedId = box.id, page = StaffPage.DETAIL)
        saved["staff.selected"] = box.id; saved["staff.page"] = StaffPage.DETAIL.name
        clearDraft()
    }
    fun clear() { generation++; saved.keys().filter { it.startsWith("staff.") }.forEach { saved.remove<Any>(it) }; state = StaffPackingUiState(now = clock()) }
    private fun clearDraft() {
        saved.remove<ArrayList<String>>("staff.draft"); saved.remove<ArrayList<String>>("staff.lines"); saved.remove<Int>("staff.step")
        state = state.copy(draft = PackingDraft(), step = 0, issues = emptySet(), confirmDiscard = false)
    }
    private fun persistDraft() { with(state.draft) {
        saved["staff.draft"] = arrayListOf(id, batchId, expiresAt.toString(), storage?.name.orEmpty(), foodChecked.toString(), packagingChecked.toString(), allergensChecked.toString())
        saved["staff.lines"] = ArrayList(selections.flatMap { listOf(it.itemId, it.quantity, it.unitPrice.toString(), it.unit.name, it.updatedAt.toString()) })
        saved["staff.step"] = state.step
    } }
    private fun restoreDraft(): PackingDraft {
        val values = saved.get<ArrayList<String>>("staff.draft") ?: return PackingDraft()
        if (values.size != 7) return PackingDraft()
        val lines = saved.get<ArrayList<String>>("staff.lines").orEmpty().chunked(5).filter { it.size == 5 }.mapNotNull { row ->
            val unit = enumValue<MenuUnit>(row[3]) ?: return@mapNotNull null
            PackingSelection(row[0], row[1], row[2].toLongOrNull() ?: 0, unit, row[4].toLongOrNull() ?: 0)
        }
        return PackingDraft(values[0], values[1], lines, values[2].toLongOrNull() ?: 0, enumValue<BoxStorage>(values[3]), values[4].toBoolean(), values[5].toBoolean(), values[6].toBoolean())
    }
    private inline fun <reified T : Enum<T>> enumValue(value: String?): T? = enumValues<T>().firstOrNull { it.name == value }
}
