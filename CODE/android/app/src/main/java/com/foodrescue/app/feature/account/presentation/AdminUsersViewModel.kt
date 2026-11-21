package com.foodrescue.app.feature.account.presentation

import androidx.compose.runtime.*
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foodrescue.app.feature.account.domain.*
import kotlinx.coroutines.*
import java.util.UUID

internal class AdminUsersViewModel(private val saved: SavedStateHandle, private val repository: AccountManagementRepository) : ViewModel() {
    var state by mutableStateOf(AdminUsersState(query = saved["users.query"] ?: "", selectedKey = saved["users.selected"],
        role = AccountRole.entries.find { it.name == saved.get<String>("users.role") },
        status = AccountStatus.entries.find { it.name == saved.get<String>("users.status") }))
        private set
    private var work: Job? = null
    private var generation = 0

    fun refresh() {
        if (state.busy || state.draft != null) return
        val request = ++generation
        state = state.copy(busy = true, error = null)
        work = viewModelScope.launch {
            try {
                val accounts = repository.accounts(); ensureActive()
                if (request == generation) state = state.copy(accounts = accounts,
                    selectedKey = state.selectedKey?.takeIf { key -> accounts.any { it.key == key } })
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                if (request == generation) state = state.copy(error = "Chưa tải được danh sách tài khoản. Vui lòng thử lại.")
            } finally { if (request == generation) state = state.copy(busy = false, loading = false) }
        }
    }
    fun search(value: String) { state = state.copy(query = value.take(150)); saved["users.query"] = state.query }
    fun role(value: AccountRole?) { state = state.copy(role = value); saved["users.role"] = value?.name }
    fun status(value: AccountStatus?) { state = state.copy(status = value); saved["users.status"] = value?.name }
    fun open(key: String) {
        if (state.busy || state.accounts.none { it.key == key }) return
        state = state.copy(selectedKey = key, error = null); saved["users.selected"] = key
        refresh()
    }
    fun back() {
        if (state.busy) return
        if (state.draft != null) state = state.copy(draft = null, confirming = false, attempted = false)
        else { state = state.copy(selectedKey = null, error = null); saved.remove<String>("users.selected") }
    }
    fun prepare() {
        val user = state.selected ?: return
        if (state.busy || state.error != null || user.protected) return
        state = state.copy(draft = AccountDecision(UUID.randomUUID().toString(), user.record.revision,
            if (user.record.status == AccountStatus.ACTIVE) AccountStatus.SUSPENDED else AccountStatus.ACTIVE),
            attempted = false, confirming = false, error = null)
    }
    fun reason(value: String) {
        if (!state.busy) state = state.copy(draft = state.draft?.copy(reason = value.take(500)), attempted = false, confirming = false)
    }
    fun review() {
        if (!state.busy) state = state.copy(attempted = true, confirming = state.draft?.let { validAccountReason(it.reason) } == true)
    }
    fun dismiss() { if (!state.busy) state = state.copy(confirming = false) }
    fun confirm() {
        val selected = state.selected ?: return
        val draft = state.draft ?: return
        if (state.busy || !state.confirming || !validAccountReason(draft.reason) || selected.protected) return
        val request = ++generation
        state = state.copy(busy = true, error = null)
        work = viewModelScope.launch {
            try {
                val updated = repository.decide(selected.key, draft); ensureActive()
                if (request == generation) state = state.copy(accounts = state.accounts.map { if (it.key == updated.key) updated else it },
                    draft = null, confirming = false, attempted = false, notice = "Đã cập nhật trạng thái tài khoản")
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: StaleAccountDecision) {
                if (request == generation) state = state.copy(draft = null, confirming = false,
                    error = "Tài khoản đã thay đổi. Làm mới và kiểm tra trước khi quyết định.")
            } catch (_: ProtectedAccount) {
                if (request == generation) state = state.copy(draft = null, confirming = false,
                    error = "Tài khoản quản trị được bảo vệ và không thể khóa tại đây.")
            } catch (_: Exception) {
                if (request == generation) state = state.copy(confirming = false,
                    error = "Chưa lưu được thay đổi. Lý do của bạn vẫn được giữ lại.")
            } finally { if (request == generation) state = state.copy(busy = false) }
        }
    }
    fun consumeNotice() { state = state.copy(notice = null) }
    fun clear() {
        generation++; work?.cancel(); work = null
        listOf("query", "selected", "role", "status").forEach { saved.remove<String>("users.$it") }
        state = AdminUsersState()
    }
}

