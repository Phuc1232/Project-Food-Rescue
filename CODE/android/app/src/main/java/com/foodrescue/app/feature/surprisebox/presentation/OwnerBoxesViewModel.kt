package com.foodrescue.app.feature.surprisebox.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foodrescue.app.feature.surprisebox.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class OwnerBoxesViewModel(private val saved: SavedStateHandle, private val repository: OwnerBoxRepository,
    private val clock: () -> Long = System::currentTimeMillis) : ViewModel() {
    var state by mutableStateOf(OwnerBoxesUiState(
        page = enumValue<OwnerBoxPage>(saved["boxes.page"]) ?: OwnerBoxPage.LIST,
        section = enumValue<OwnerBoxSection>(saved["boxes.section"]) ?: OwnerBoxSection.BATCHES,
        selectedId = saved["boxes.selected"], query = saved["boxes.query"] ?: "",
        filter = enumValue<BatchStatus>(saved["boxes.filter"]), now = clock(),
        templateDraft = restoreTemplate(), batchDraft = restoreBatch("boxes.draft"), initialBatchDraft = restoreBatch("boxes.initial"),
    ))
        private set

    fun load() {
        if (state.busy) return
        state = state.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val catalog = repository.load()
                state = state.copy(catalog = catalog, loading = false, now = clock())
                if (state.page == OwnerBoxPage.BATCH_DETAIL && state.selected == null) navigate(OwnerBoxPage.LIST)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { state = state.copy(error = "Chưa tải được hộp của cửa hàng. Vui lòng thử lại.")
            } finally { state = state.copy(busy = false) }
        }
    }
    fun refreshTime() { state = state.copy(now = clock()) }
    fun section(value: OwnerBoxSection) { if (state.busy) return; state = state.copy(section = value, query = ""); saved["boxes.section"] = value.name; saved["boxes.query"] = "" }
    fun search(value: String) { state = state.copy(query = value.take(100)); saved["boxes.query"] = state.query }
    fun filter(value: BatchStatus?) { state = state.copy(filter = value); saved["boxes.filter"] = value?.name }
    fun consumeMessage() { state = state.copy(message = null) }
    fun beginTemplate(template: BoxTemplate? = null) {
        if (state.busy) return
        state = state.copy(templateDraft = template?.toDraft() ?: BoxTemplateDraft())
        rememberTemplate(); navigate(OwnerBoxPage.TEMPLATE_EDIT)
    }
    fun editTemplate(draft: BoxTemplateDraft) {
        if (state.busy) return
        state = state.copy(templateDraft = draft.copy(name = draft.name.take(80), description = draft.description.take(500),
            allergens = draft.allergens.take(250), price = draft.price.take(12), minimumValue = draft.minimumValue.take(12)), fields = emptySet(), error = null)
        rememberTemplate()
    }
    fun beginBatch(templateId: String? = null, batch: SaleBatch? = null) {
        if (state.busy) return
        val active = state.catalog.templates.filter { it.active }
        if (batch == null && active.isEmpty()) {
            section(OwnerBoxSection.TEMPLATES); state = state.copy(message = "Tạo hoặc mở lại một mẫu hộp trước khi lập đợt bán."); return
        }
        val now = clock()
        val start = (now / 60_000 + 1) * 60_000
        val draft = batch?.toDraft() ?: SaleBatchDraft(templateId = templateId ?: active.first().id, salesStart = start,
            salesEnd = start + 3_600_000, pickupStart = start + 3_600_000, pickupEnd = start + 7_200_000)
        state = state.copy(batchDraft = draft, initialBatchDraft = draft)
        rememberBatch("boxes.initial", draft); rememberBatch("boxes.draft", draft); navigate(OwnerBoxPage.BATCH_EDIT)
    }
    fun editBatch(draft: SaleBatchDraft) {
        if (state.busy) return
        state = state.copy(batchDraft = draft.copy(quantity = draft.quantity.take(8)), fields = emptySet(), error = null)
        rememberBatch("boxes.draft", state.batchDraft)
    }
    fun openBatch(id: String) {
        if (state.busy) return
        state = state.copy(selectedId = id); saved["boxes.selected"] = id; navigate(OwnerBoxPage.BATCH_DETAIL)
    }
    fun saveTemplate() {
        val draft = state.templateDraft
        val errors = draft.validationErrors(state.catalog.templates)
        state = state.copy(fields = errors)
        if (errors.isNotEmpty()) return
        action {
            val catalog = repository.saveTemplate(draft)
            state = state.copy(catalog = catalog, section = OwnerBoxSection.TEMPLATES, query = "", message = "Đã lưu mẫu hộp")
            saved.remove<ArrayList<String>>("boxes.template"); saved["boxes.section"] = OwnerBoxSection.TEMPLATES.name; saved["boxes.query"] = ""
            navigate(OwnerBoxPage.LIST)
        }
    }
    fun saveBatch() {
        val draft = state.batchDraft
        val errors = draft.validationErrors(state.catalog, clock())
        state = state.copy(fields = errors)
        if (errors.isNotEmpty()) return
        action {
            val beforeIds = state.catalog.batches.map { it.id }.toSet()
            val catalog = repository.saveBatch(draft)
            val id = draft.id ?: catalog.batches.first { it.id !in beforeIds }.id
            state = state.copy(catalog = catalog, selectedId = id, section = OwnerBoxSection.BATCHES, message = "Đã lưu đợt bán")
            saved["boxes.selected"] = id; saved["boxes.section"] = OwnerBoxSection.BATCHES.name
            saved.remove<ArrayList<String>>("boxes.draft"); saved.remove<ArrayList<String>>("boxes.initial")
            navigate(OwnerBoxPage.BATCH_DETAIL)
        }
    }
    fun requestAction(value: BatchAction) { if (!state.busy) state = state.copy(pendingAction = value, error = null) }
    fun requestToggle(id: String) { if (!state.busy) state = state.copy(toggleTemplateId = id, error = null) }
    fun dismissConfirmation() { if (!state.busy) state = state.copy(pendingAction = null, toggleTemplateId = null, error = null) }
    fun confirmAction() {
        val id = state.selectedId ?: return
        val operation = state.pendingAction ?: return
        action {
            val catalog = repository.operate(id, operation)
            state = state.copy(catalog = catalog, pendingAction = null, message = "Đã cập nhật trạng thái đợt bán")
        }
    }
    fun confirmToggle() {
        val template = state.catalog.templates.firstOrNull { it.id == state.toggleTemplateId } ?: return
        action {
            val catalog = repository.setTemplateActive(template.id, !template.active)
            state = state.copy(catalog = catalog, toggleTemplateId = null,
                message = if (template.active) "Đã ngừng sử dụng mẫu hộp" else "Đã mở lại mẫu hộp")
        }
    }
    fun back(onClose: () -> Unit) {
        if (state.busy) return
        if (state.dirty) { state = state.copy(confirmDiscard = true); return }
        when (state.page) { OwnerBoxPage.LIST -> onClose(); else -> discard() }
    }
    fun keepEditing() { state = state.copy(confirmDiscard = false) }
    fun discard() {
        if (state.busy) return
        val detail = state.page == OwnerBoxPage.BATCH_EDIT && state.batchDraft.id != null
        saved.remove<ArrayList<String>>("boxes.template"); saved.remove<ArrayList<String>>("boxes.draft"); saved.remove<ArrayList<String>>("boxes.initial")
        state = state.copy(confirmDiscard = false)
        navigate(if (detail) OwnerBoxPage.BATCH_DETAIL else OwnerBoxPage.LIST)
    }
    fun clearTransient() {
        if (state.busy) return
        saved.keys().filter { it.startsWith("boxes.") }.forEach { saved.remove<Any>(it) }
        state = OwnerBoxesUiState()
    }
    private fun navigate(page: OwnerBoxPage) {
        state = state.copy(page = page, fields = emptySet(), error = null, pendingAction = null, toggleTemplateId = null)
        saved["boxes.page"] = page.name
    }
    private fun action(block: suspend () -> Unit) {
        if (state.busy || state.loading) return
        state = state.copy(busy = true, error = null)
        viewModelScope.launch {
            try { withContext(NonCancellable) { block() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (invalid: BoxValidationException) { state = state.copy(fields = invalid.fields, error = "Kiểm tra lại thông tin được đánh dấu.") }
            catch (_: Exception) { state = state.copy(error = "Chưa lưu được thay đổi. Kiểm tra trạng thái đợt bán và thử lại.") }
            finally { state = state.copy(busy = false, now = clock()) }
        }
    }
    private fun rememberTemplate() { with(state.templateDraft) {
        saved["boxes.template"] = arrayListOf(id.orEmpty(), name, category.name, description, allergens, minimumValue, price)
    } }
    private fun restoreTemplate(): BoxTemplateDraft {
        val values = saved.get<ArrayList<String>>("boxes.template") ?: return BoxTemplateDraft()
        if (values.size != 7) return BoxTemplateDraft()
        return BoxTemplateDraft(values[0].ifEmpty { null }, values[1], enumValue<BoxCategory>(values[2]) ?: BoxCategory.BAKERY,
            values[3], values[4], values[5], values[6])
    }
    private fun rememberBatch(key: String, draft: SaleBatchDraft) { with(draft) {
        saved[key] = arrayListOf(id.orEmpty(), templateId, quantity, salesStart.toString(), salesEnd.toString(), pickupStart.toString(), pickupEnd.toString())
    } }
    private fun restoreBatch(key: String): SaleBatchDraft {
        val values = saved.get<ArrayList<String>>(key) ?: return SaleBatchDraft()
        if (values.size != 7) return SaleBatchDraft()
        return SaleBatchDraft(values[0].ifEmpty { null }, values[1], values[2], values[3].toLongOrNull() ?: 0,
            values[4].toLongOrNull() ?: 0, values[5].toLongOrNull() ?: 0, values[6].toLongOrNull() ?: 0)
    }
    private inline fun <reified T : Enum<T>> enumValue(raw: String?): T? = enumValues<T>().firstOrNull { it.name == raw }
}
