package com.foodrescue.app.feature.surprisebox.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foodrescue.app.feature.surprisebox.domain.*
import com.foodrescue.app.feature.menu.domain.MenuRepository
import com.foodrescue.app.feature.menu.domain.MenuStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class OwnerBoxesViewModel(private val saved: SavedStateHandle, private val repository: OwnerBoxRepository,
    private val menuRepository: MenuRepository,
    private val clock: () -> Long = System::currentTimeMillis) : ViewModel() {
    private var valueJob: Job? = null
    var state by mutableStateOf(OwnerBoxesUiState(
        page = enumValue<OwnerBoxPage>(saved["boxes.page"]) ?: OwnerBoxPage.LIST,
        section = enumValue<OwnerBoxSection>(saved["boxes.section"]) ?: OwnerBoxSection.BATCHES,
        selectedId = saved["boxes.selected"], query = saved["boxes.query"] ?: "",
        filter = enumValue<BatchStatus>(saved["boxes.filter"]), now = clock(),
        templateDraft = restoreTemplate(), batchDraft = restoreBatch("boxes.draft"), initialBatchDraft = restoreBatch("boxes.initial"),
        calculator = BoxValueCalculatorState(quantities = restoreValueQuantities(), query = saved["boxes.value.query"] ?: ""),
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
        resetValueCalculator()
        state = state.copy(templateDraft = template?.toDraft() ?: BoxTemplateDraft())
        rememberTemplate(); navigate(OwnerBoxPage.TEMPLATE_EDIT)
    }
    fun editTemplate(draft: BoxTemplateDraft) {
        if (state.busy) return
        state = state.copy(templateDraft = draft.copy(name = draft.name.take(80), description = draft.description.take(500),
            allergens = draft.allergens.take(250), price = draft.price.take(12), minimumValue = draft.minimumValue.take(12)), fields = emptySet(), error = null)
        rememberTemplate()
    }
    fun openValueCalculator() {
        if (state.busy || state.page != OwnerBoxPage.TEMPLATE_EDIT) return
        state = state.copy(calculator = state.calculator.copy(loading = true, error = null, notice = null))
        navigate(OwnerBoxPage.VALUE_CALCULATOR)
    }
    fun searchValueMenu(query: String) {
        state = state.copy(calculator = state.calculator.copy(query = query.take(100)))
        saved["boxes.value.query"] = state.calculator.query
    }
    fun valueQuantity(id: String, quantity: String?) {
        if (state.page != OwnerBoxPage.VALUE_CALCULATOR || state.calculator.loading) return
        val item = state.calculator.menu.items.firstOrNull { it.id == id }
        if (quantity != null && item?.status != MenuStatus.ACTIVE) return
        val values = state.calculator.quantities.toMutableMap()
        if (quantity == null) values.remove(id) else values[id] = quantity.take(12)
        state = state.copy(calculator = state.calculator.copy(quantities = values, notice = null))
        saved["boxes.value.quantities"] = ArrayList(values.flatMap { listOf(it.key, it.value) })
    }
    fun loadValueMenu() = refreshValueMenu(apply = false)
    fun applyValueEstimate() {
        if (state.calculator.canApply) refreshValueMenu(apply = true)
    }
    private fun refreshValueMenu(apply: Boolean) {
        if (state.page != OwnerBoxPage.VALUE_CALCULATOR || valueJob?.isActive == true) return
        val before = state.calculator
        state = state.copy(calculator = before.copy(loading = true, error = null, notice = null))
        valueJob = viewModelScope.launch {
            try {
                val menu = menuRepository.load()
                ensureActive()
                val previous = before.menu.items.associateBy { it.id }
                val current = menu.items.associateBy { it.id }
                val changed = before.quantities.keys.any { id ->
                    val old = previous[id]; val fresh = current[id]
                    old?.price != fresh?.price || old?.unit != fresh?.unit || old?.status != fresh?.status
                }
                val calculator = state.calculator.copy(menu = menu, loading = false,
                    notice = if (apply && changed) "Menu vừa thay đổi. Kiểm tra lại giá và món đã chọn trước khi áp dụng." else null)
                state = state.copy(calculator = calculator)
                if (apply && !changed && calculator.canApply) {
                    state = state.copy(templateDraft = state.templateDraft.copy(minimumValue = calculator.estimate.total.toString()))
                    rememberTemplate()
                    navigate(OwnerBoxPage.TEMPLATE_EDIT)
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                ensureActive()
                state = state.copy(calculator = state.calculator.copy(loading = false,
                    error = "Chưa tải được menu. Vui lòng thử lại."))
            }
        }
    }
    private fun resetValueCalculator() {
        valueJob?.cancel(); valueJob = null
        saved.remove<ArrayList<String>>("boxes.value.quantities"); saved.remove<String>("boxes.value.query")
        state = state.copy(calculator = BoxValueCalculatorState())
    }
    private fun restoreValueQuantities(): Map<String, String> =
        saved.get<ArrayList<String>>("boxes.value.quantities").orEmpty().chunked(2)
            .filter { it.size == 2 }.associate { it[0] to it[1] }
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
            resetValueCalculator()
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
        if (state.page == OwnerBoxPage.VALUE_CALCULATOR) {
            valueJob?.cancel(); valueJob = null
            navigate(OwnerBoxPage.TEMPLATE_EDIT)
            return
        }
        if (state.busy) return
        if (state.dirty) { state = state.copy(confirmDiscard = true); return }
        when (state.page) { OwnerBoxPage.LIST -> onClose(); else -> discard() }
    }
    fun keepEditing() { state = state.copy(confirmDiscard = false) }
    fun discard() {
        if (state.busy) return
        val detail = state.page == OwnerBoxPage.BATCH_EDIT && state.batchDraft.id != null
        resetValueCalculator()
        saved.remove<ArrayList<String>>("boxes.template"); saved.remove<ArrayList<String>>("boxes.draft"); saved.remove<ArrayList<String>>("boxes.initial")
        state = state.copy(confirmDiscard = false)
        navigate(if (detail) OwnerBoxPage.BATCH_DETAIL else OwnerBoxPage.LIST)
    }
    fun clearTransient() {
        if (state.busy) return
        valueJob?.cancel(); valueJob = null
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
