package com.foodrescue.app.feature.menu.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foodrescue.app.feature.menu.domain.*
import kotlinx.coroutines.*

internal class MenuViewModel(private val saved: SavedStateHandle, private val repository: MenuRepository,
    private val assets: MenuAssetStore) : ViewModel() {
    var state by mutableStateOf(MenuUiState(
        page = enumValue<MenuPage>(saved["menu.page"]) ?: MenuPage.LIST,
        selectedId = saved["menu.selected"], query = saved["menu.query"] ?: "",
        category = enumValue<MenuCategory>(saved["menu.category"]), status = enumValue<MenuStatus>(saved["menu.status"]),
        sort = enumValue<MenuSort>(saved["menu.sort"]) ?: MenuSort.UPDATED,
    ))
        private set

    fun load() {
        if (state.busy) return
        state = state.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val menu = repository.load()
                val draft = restoreDraft() ?: state.draft
                val page = if (state.page == MenuPage.DETAIL && menu.items.none { it.id == state.selectedId }) MenuPage.LIST else state.page
                state = state.copy(menu = menu, draft = draft, page = page, loading = false)
                assets.prune(menu.assetIds + listOfNotNull(draft.photo?.id))
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { state = state.copy(error = "Chưa tải được menu. Vui lòng thử lại.")
            } finally { state = state.copy(busy = false); consumePendingImport() }
        }
    }

    fun search(query: String) { state = state.copy(query = query.take(100)); saved["menu.query"] = state.query }
    fun filterCategory(category: MenuCategory?) { state = state.copy(category = category); saved["menu.category"] = category?.name }
    fun filterStatus(status: MenuStatus?) { state = state.copy(status = status); saved["menu.status"] = status?.name }
    fun sort(sort: MenuSort) { state = state.copy(sort = sort); saved["menu.sort"] = sort.name }
    fun resetFilters() { search(""); filterCategory(null); filterStatus(null) }

    fun openItem(id: String) {
        if (state.busy) return
        saved["menu.selected"] = id
        state = state.copy(selectedId = id, error = null)
        navigate(MenuPage.DETAIL)
    }
    fun documents() { if (!state.busy) navigate(MenuPage.DOCUMENTS) }
    fun beginEdit(item: MenuItem? = null) {
        if (state.busy) return
        state = state.copy(draft = item?.toDraft() ?: MenuDraft(), errors = emptySet(), error = null)
        rememberDraft()
        navigate(MenuPage.EDIT)
    }
    fun editName(value: String) = edit(state.draft.copy(name = value.take(100)))
    fun editPrice(value: String) = edit(state.draft.copy(price = value.take(12)))
    fun editDescription(value: String) = edit(state.draft.copy(description = value.take(500)))
    fun editCategory(value: MenuCategory) = edit(state.draft.copy(category = value))
    fun editUnit(value: MenuUnit) = edit(state.draft.copy(unit = value))
    private fun edit(draft: MenuDraft) {
        if (state.busy) return
        state = state.copy(draft = draft, errors = emptySet(), error = null)
        rememberDraft()
    }

    fun back(onClose: () -> Unit) {
        if (state.busy) return
        when (state.page) {
            MenuPage.LIST -> onClose()
            MenuPage.EDIT -> if (state.dirty) state = state.copy(confirmExit = true) else discardEdit()
            else -> navigate(MenuPage.LIST)
        }
    }
    fun keepEditing() { state = state.copy(confirmExit = false) }
    fun discardEdit() {
        if (state.busy) return
        val photo = state.draft.photo?.takeIf { it.id !in state.menu.assetIds }
        val returnPage = if (state.draft.id == null) MenuPage.LIST else MenuPage.DETAIL
        saved.remove<ArrayList<String>>("menu.draft")
        state = state.copy(draft = MenuDraft(), errors = emptySet(), error = null, confirmExit = false)
        navigate(returnPage)
        if (photo != null) viewModelScope.launch { assets.remove(photo) }
    }

    fun save() {
        if (state.busy) return
        val draft = state.draft
        val errors = draft.validationErrors(state.menu.items)
        state = state.copy(errors = errors)
        if (errors.isNotEmpty()) return
        runAction { withContext(NonCancellable) {
            val menu = repository.save(draft)
            val item = if (draft.id != null) menu.items.first { it.id == draft.id }
                else menu.items.first { candidate -> state.menu.items.none { it.id == candidate.id } }
            state = state.copy(menu = menu, draft = item.toDraft(), selectedId = item.id, message = if (draft.id == null) "Đã thêm món vào menu" else "Đã lưu thay đổi")
            saved.remove<ArrayList<String>>("menu.draft")
            saved["menu.selected"] = item.id
            navigate(MenuPage.DETAIL)
        } }
    }

    fun requestStatus(id: String) { if (!state.busy) state = state.copy(statusTarget = id) }
    fun cancelStatus() { state = state.copy(statusTarget = null) }
    fun confirmStatus() {
        val item = state.menu.items.firstOrNull { it.id == state.statusTarget } ?: return
        runAction { withContext(NonCancellable) {
            val status = if (item.status == MenuStatus.ACTIVE) MenuStatus.INACTIVE else MenuStatus.ACTIVE
            val menu = repository.setStatus(item.id, status)
            state = state.copy(menu = menu, statusTarget = null, message = if (status == MenuStatus.ACTIVE) "Đã mở kinh doanh lại món" else "Đã ngừng kinh doanh món")
        } }
    }

    fun importPhoto(uri: String) = acceptImport(uri, document = false)
    private fun importPhotoNow(uri: String) = runAction {
        val photo = assets.import(uri, false)
        val previous = state.draft.photo
        state = state.copy(draft = state.draft.copy(photo = photo))
        rememberDraft()
        if (previous != null && previous.id !in state.menu.assetIds) assets.remove(previous)
    }
    fun removePhoto() {
        if (state.busy) return
        val photo = state.draft.photo
        edit(state.draft.copy(photo = null))
        if (photo != null && photo.id !in state.menu.assetIds) viewModelScope.launch { assets.remove(photo) }
    }

    fun importDocument(uri: String) = acceptImport(uri, document = true)
    private fun importDocumentNow(uri: String) {
        if (state.menu.documents.size >= MAX_MENU_DOCUMENTS) { state = state.copy(error = MenuAssetIssue.LIMIT.message); return }
        runAction {
            val document = assets.import(uri, true)
            withContext(NonCancellable) {
                try { state = state.copy(menu = repository.addDocument(document), message = "Đã thêm tài liệu") }
                catch (error: Exception) { assets.remove(document); throw error }
            }
        }
    }
    private fun acceptImport(uri: String, document: Boolean) {
        saved["menu.pendingImport"] = arrayListOf(uri, document.toString())
        consumePendingImport()
    }
    private fun consumePendingImport() {
        if (state.busy || state.loading) return
        val pending = saved.remove<ArrayList<String>>("menu.pendingImport") ?: return
        if (pending.size != 2) return
        if (pending[1] == "true") importDocumentNow(pending[0])
        else if (state.page == MenuPage.EDIT) importPhotoNow(pending[0])
    }
    fun requestRemoveDocument(id: String) { if (!state.busy) state = state.copy(documentToRemove = id) }
    fun cancelRemoveDocument() { state = state.copy(documentToRemove = null) }
    fun removeDocument() {
        val id = state.documentToRemove ?: return
        runAction { withContext(NonCancellable) {
            state = state.copy(menu = repository.removeDocument(id), documentToRemove = null, message = "Đã gỡ tài liệu")
        } }
    }
    fun pickerError() { state = state.copy(error = "Chưa mở được trình chọn tệp. Vui lòng thử lại.") }
    fun consumeMessage() { state = state.copy(message = null) }
    fun clearTransient() {
        if (!state.busy) {
            saved.remove<ArrayList<String>>("menu.pendingImport")
            discardEdit(); navigate(MenuPage.LIST); state = state.copy(selectedId = null, error = null, message = null)
        }
    }
    private fun navigate(page: MenuPage) { state = state.copy(page = page, error = null); saved["menu.page"] = page.name }

    private fun runAction(action: suspend () -> Unit) {
        if (state.busy || state.loading) return
        state = state.copy(busy = true, error = null)
        viewModelScope.launch {
            try { action()
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (invalid: MenuValidationException) { state = state.copy(errors = invalid.fields)
            } catch (asset: MenuAssetException) { state = state.copy(error = asset.issue.message)
            } catch (_: Exception) { state = state.copy(error = "Chưa lưu được thay đổi. Nội dung của bạn vẫn được giữ lại, vui lòng thử lại.")
            } finally { state = state.copy(busy = false); consumePendingImport() }
        }
    }

    private fun rememberDraft() {
        val draft = state.draft
        saved["menu.draft"] = arrayListOf(draft.id.orEmpty(), draft.name, draft.category.name, draft.unit.name, draft.price, draft.description, draft.status.name).apply {
            draft.photo?.let { addAll(listOf(it.id, it.name, it.mimeType, it.byteCount.toString(), it.previewUri, it.sourceUri, it.pageCount.toString())) }
        }
    }
    private fun restoreDraft(): MenuDraft? {
        val fields = saved.get<ArrayList<String>>("menu.draft") ?: return null
        if (fields.size != 7 && fields.size != 14) return null
        return runCatching { MenuDraft(fields[0].ifBlank { null }, fields[1], MenuCategory.valueOf(fields[2]), MenuUnit.valueOf(fields[3]), fields[4], fields[5],
            MenuStatus.valueOf(fields[6]), if (fields.size == 14) MenuAsset(fields[7], fields[8], fields[9], fields[10].toLong(), fields[11], fields[12], fields[13].toInt()) else null) }.getOrNull()
    }
    private inline fun <reified T : Enum<T>> enumValue(value: String?): T? = value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }
}
