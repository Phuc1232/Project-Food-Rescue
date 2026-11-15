package com.foodrescue.app.feature.order.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foodrescue.app.feature.order.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

internal class OwnerOrdersViewModel(private val saved: SavedStateHandle, private val repository: OwnerOrderRepository,
    private val clock: () -> Long = System::currentTimeMillis) : ViewModel() {
    private var operation: Job? = null
    private var generation = 0
    var state by mutableStateOf(OwnerOrdersUiState(now = clock(), query = saved["ownerOrders.query"] ?: "",
        filter = enum<OwnerOrderFilter>(saved["ownerOrders.filter"]) ?: OwnerOrderFilter.ALL,
        pickupFilter = enum<OwnerPickupFilter>(saved["ownerOrders.pickup"]) ?: OwnerPickupFilter.ALL,
        page = enum<OwnerOrderPage>(saved["ownerOrders.page"]) ?: OwnerOrderPage.LIST, selectedId = saved["ownerOrders.id"],
        draft = saved.get<ArrayList<String>>("ownerOrders.draft")?.takeIf { it.size == 3 }?.let {
            OwnerCancellationDraft(it[0], enum<OwnerCancellationReason>(it[1]), it[2])
        }))
        private set

    suspend fun refresh() {
        if (state.busy) return
        val request = ++generation
        try {
            val orders = repository.load()
            currentCoroutineContext().ensureActive()
            if (request != generation || state.busy) return
            state = state.copy(orders = orders, now = clock(), loading = false, loadError = null)
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) {
            if (request == generation) state = state.copy(loading = false, loadError = "Chưa tải được đơn hàng. Vui lòng thử lại.")
        }
    }
    fun reload() { if (operation?.isActive != true) operation = viewModelScope.launch { refresh() } }
    fun tick() { state = state.copy(now = clock()) }
    fun search(query: String) { state = state.copy(query = query.take(100)); saved["ownerOrders.query"] = state.query }
    fun filter(filter: OwnerOrderFilter) { state = state.copy(filter = filter); saved["ownerOrders.filter"] = filter.name }
    fun pickup(filter: OwnerPickupFilter) { state = state.copy(pickupFilter = filter); saved["ownerOrders.pickup"] = filter.name }
    fun open(id: String) { if (!state.busy) { state = state.copy(selectedId = id, draft = null, error = null); page(OwnerOrderPage.DETAIL); saveDraft() } }
    fun beginCancellation() {
        val order = state.selected ?: return
        if (state.busy || state.loadError != null || order.ownerCancellation != null || !order.isPaid || order.cancelledAtMillis != null || order.collectedAtMillis != null) return
        state = state.copy(draft = OwnerCancellationDraft(order.id), error = null); saveDraft(); page(OwnerOrderPage.CANCELLATION)
    }
    fun reason(reason: OwnerCancellationReason) { edit(state.draft?.copy(reason = reason)) }
    fun note(note: String) { edit(state.draft?.copy(note = note.take(500))) }
    private fun edit(draft: OwnerCancellationDraft?) { if (!state.busy) { state = state.copy(draft = draft, error = null, confirm = false); saveDraft() } }
    fun review() {
        val order = state.selected ?: return
        val draft = state.draft ?: return
        val issue = ownerCancellationIssue(order, draft)
        state = state.copy(error = issue?.message, confirm = issue == null)
    }
    fun dismissConfirmation() { if (!state.busy) state = state.copy(confirm = false) }
    fun submit() {
        if (!state.confirm || state.busy || state.loadError != null) return
        val draft = state.draft ?: return
        generation++
        state = state.copy(busy = true, error = null)
        operation = viewModelScope.launch {
            try {
                val orders = repository.requestCancellation(draft)
                currentCoroutineContext().ensureActive()
                state = state.copy(orders = orders, draft = null, confirm = false, message = "Đã gửi yêu cầu hủy. Đang chờ xử lý.")
                saveDraft(); page(OwnerOrderPage.DETAIL)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: OwnerOrderException) { state = state.copy(error = error.issue.message, confirm = false)
            } catch (_: Exception) { state = state.copy(error = "Chưa gửi được yêu cầu. Vui lòng thử lại.", confirm = false)
            } finally { state = state.copy(busy = false) }
        }
    }
    fun back() {
        if (state.busy) return
        state = state.copy(confirm = false, error = null)
        if (state.page == OwnerOrderPage.CANCELLATION) { state = state.copy(draft = null); saveDraft(); page(OwnerOrderPage.DETAIL) }
        else { state = state.copy(selectedId = null); page(OwnerOrderPage.LIST) }
    }
    fun consumeMessage() { state = state.copy(message = null) }
    fun clear() { generation++; operation?.cancel(); operation = null; saved.keys().filter { it.startsWith("ownerOrders.") }.forEach { saved.remove<Any>(it) }; state = OwnerOrdersUiState(now = clock()) }
    private fun page(page: OwnerOrderPage) { state = state.copy(page = page); saved["ownerOrders.page"] = page.name; saved["ownerOrders.id"] = state.selectedId }
    private fun saveDraft() { saved["ownerOrders.draft"] = state.draft?.let { arrayListOf(it.orderId, it.reason?.name.orEmpty(), it.note) } }
    private inline fun <reified T : Enum<T>> enum(value: String?): T? = value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }
}
