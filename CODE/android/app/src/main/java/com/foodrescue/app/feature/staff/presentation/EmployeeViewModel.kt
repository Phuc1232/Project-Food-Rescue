package com.foodrescue.app.feature.staff.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foodrescue.app.feature.staff.domain.*
import kotlinx.coroutines.*

internal class EmployeeViewModel(private val saved: SavedStateHandle, private val repository: EmployeeRepository) : ViewModel() {
    private var generation = 0
    private var operation: Job? = null
    var state by mutableStateOf(EmployeeUiState(page = enum<EmployeePage>(saved["employees.page"]) ?: EmployeePage.LIST,
        query = saved["employees.query"] ?: "", filter = enum<EmployeeFilter>(saved["employees.filter"]) ?: EmployeeFilter.ALL,
        selectedId = saved["employees.selected"], draft = restoreDraft(), step = (saved.get<Int>("employees.step") ?: 0).coerceIn(0, 2),
        accessTarget = enum<EmployeeStatus>(saved["employees.accessTarget"]), accessRevision = saved["employees.accessRevision"] ?: 0,
        reason = saved["employees.reason"] ?: ""))
        private set

    suspend fun refresh() {
        if (state.busy) return
        val request = ++generation
        try {
            val members = repository.load()
            currentCoroutineContext().ensureActive()
            if (request == generation && !state.busy) state = state.copy(employees = members, loading = false, loadError = null)
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: Exception) {
            if (request == generation) state = state.copy(loading = false,
                loadError = (error as? EmployeeException)?.issue?.message ?: "Chưa tải được danh sách nhân viên. Vui lòng thử lại.")
        }
    }
    fun reload() { if (operation?.isActive != true) operation = viewModelScope.launch { refresh() } }
    fun search(value: String) { state = state.copy(query = value.take(120)); saved["employees.query"] = state.query }
    fun filter(value: EmployeeFilter) { state = state.copy(filter = value); saved["employees.filter"] = value.name }
    fun resetFilters() { search(""); filter(EmployeeFilter.ALL) }
    fun open(id: String) {
        if (!state.busy && state.employees.any { it.id == id }) { state = state.copy(selectedId = id, error = null); page(EmployeePage.DETAIL) }
    }
    fun beginCreate() {
        if (!state.canMutate) return
        state = state.copy(draft = EmployeeDraft(), step = 0, issues = emptySet(), error = null)
        rememberDraft(); page(EmployeePage.FORM)
    }
    fun beginEdit() {
        val member = state.selected ?: return
        if (!state.canMutate || member.status != EmployeeStatus.ACTIVE) return
        state = state.copy(draft = member.toDraft(), step = 1, issues = emptySet(), error = null)
        rememberDraft(); page(EmployeePage.FORM)
    }
    fun editName(value: String) = edit(state.draft.copy(name = value.take(120)))
    fun editEmail(value: String) { if (state.draft.id == null) edit(state.draft.copy(email = value.take(254))) }
    fun editPhone(value: String) = edit(state.draft.copy(phone = value.take(16)))
    fun permission(value: EmployeePermission, enabled: Boolean) = edit(state.draft.copy(permissions = state.draft.permissions.toggle(value, enabled)))
    fun preset(permissions: Set<EmployeePermission>) = edit(state.draft.copy(permissions = permissions.toSet()))
    private fun edit(draft: EmployeeDraft) {
        if (state.busy || state.page != EmployeePage.FORM) return
        state = state.copy(draft = draft, error = null, issues = emptySet()); rememberDraft()
    }
    fun next() {
        if (!state.canMutate) return
        val issues = state.draft.validationIssues().filter { state.step > 0 || it != EmployeeIssue.PERMISSIONS }.toSet()
        state = state.copy(issues = issues)
        if (issues.isEmpty()) { state = state.copy(step = (state.step + 1).coerceAtMost(2)); rememberDraft() }
    }
    fun save() {
        if (state.page != EmployeePage.FORM || state.step != 2 || !state.canMutate) return
        val draft = state.draft
        val issues = draft.validationIssues()
        if (issues.isNotEmpty()) { state = state.copy(issues = issues, step = if (issues.any { it != EmployeeIssue.PERMISSIONS }) 0 else 1); rememberDraft(); return }
        mutate {
            val member = repository.save(draft)
            accept(member)
            state = state.copy(draft = EmployeeDraft(), message = if (draft.id == null) "Đã tạo tài khoản nhân viên" else "Đã lưu hồ sơ và quyền truy cập")
            rememberDraft(); page(EmployeePage.DETAIL)
        }
    }
    fun requestAccess() {
        if (!state.canMutate) return
        val member = state.selected ?: return
        state = state.copy(accessTarget = if (member.status == EmployeeStatus.ACTIVE) EmployeeStatus.REVOKED else EmployeeStatus.ACTIVE,
            accessRevision = member.revision, reason = "", error = null)
        rememberAccess()
    }
    fun reason(value: String) { if (!state.busy) { state = state.copy(reason = value.take(300), error = null); rememberAccess() } }
    fun dismissAccess() { if (!state.busy) { state = state.copy(accessTarget = null, reason = "", error = null); rememberAccess() } }
    fun confirmAccess() {
        if (!state.canMutate) return
        val member = state.selected ?: return
        val target = state.accessTarget ?: return
        val revision = state.accessRevision
        try { member.changeAccess(revision, target, state.reason, System.currentTimeMillis()) }
        catch (error: EmployeeException) { state = state.copy(error = error.issue.message); return }
        val reason = state.reason
        mutate {
            accept(repository.changeAccess(member.id, revision, target, reason))
            state = state.copy(accessTarget = null, reason = "", message = if (target == EmployeeStatus.REVOKED) "Đã thu hồi quyền truy cập" else "Đã khôi phục quyền truy cập")
            rememberAccess()
        }
    }
    fun back(onClose: () -> Unit) {
        if (state.busy) return
        when {
            state.page == EmployeePage.FORM && state.step > 0 -> { state = state.copy(step = state.step - 1, error = null); rememberDraft() }
            state.page == EmployeePage.FORM && state.dirty -> state = state.copy(confirmDiscard = true)
            state.page == EmployeePage.FORM -> discard()
            state.page == EmployeePage.DETAIL -> { state = state.copy(selectedId = null, error = null); page(EmployeePage.LIST) }
            else -> onClose()
        }
    }
    fun keepEditing() { state = state.copy(confirmDiscard = false) }
    fun discard() {
        if (state.busy) return
        val destination = if (state.draft.id == null) EmployeePage.LIST else EmployeePage.DETAIL
        state = state.copy(draft = EmployeeDraft(), issues = emptySet(), error = null, confirmDiscard = false, step = 0)
        rememberDraft(); page(destination)
    }
    fun consumeMessage(message: String) { if (state.message == message) state = state.copy(message = null) }
    fun clear() {
        generation++; operation?.cancel(); operation = null
        saved.keys().filter { it.startsWith("employees.") }.forEach { saved.remove<Any>(it) }
        state = EmployeeUiState()
    }
    private fun mutate(action: suspend () -> Unit) {
        if (!state.canMutate) return
        val request = ++generation
        state = state.copy(busy = true, error = null)
        operation = viewModelScope.launch {
            try { action()
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                if (request == generation) state = state.copy(error = (error as? EmployeeException)?.issue?.message
                    ?: "Chưa lưu được thay đổi. Nội dung vẫn được giữ lại, vui lòng thử lại.")
            } finally { if (request == generation) state = state.copy(busy = false) }
        }
    }
    private suspend fun accept(member: StoreEmployee) {
        currentCoroutineContext().ensureActive()
        state = state.copy(employees = state.employees.filterNot { it.id == member.id } + member, selectedId = member.id)
    }
    private fun page(value: EmployeePage) { state = state.copy(page = value); saved["employees.page"] = value.name; saved["employees.selected"] = state.selectedId }
    private fun rememberDraft() {
        val draft = state.draft
        saved["employees.step"] = state.step
        saved["employees.draft"] = arrayListOf(draft.id.orEmpty(), draft.revision.toString(), draft.name, draft.email, draft.phone).apply {
            addAll(draft.permissions.map { it.name })
        }
    }
    private fun rememberAccess() {
        saved["employees.accessTarget"] = state.accessTarget?.name
        saved["employees.accessRevision"] = state.accessRevision
        saved["employees.reason"] = state.reason
    }
    private fun restoreDraft(): EmployeeDraft {
        val fields = saved.get<ArrayList<String>>("employees.draft") ?: return EmployeeDraft()
        return runCatching { require(fields.size >= 5); EmployeeDraft(fields[0].ifEmpty { null }, fields[1].toInt(), fields[2], fields[3], fields[4],
            fields.drop(5).map { EmployeePermission.valueOf(it) }.toSet()) }.getOrDefault(EmployeeDraft())
    }
    private inline fun <reified T : Enum<T>> enum(value: String?): T? = value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }
}
