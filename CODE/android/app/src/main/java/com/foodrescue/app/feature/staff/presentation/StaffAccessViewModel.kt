package com.foodrescue.app.feature.staff.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foodrescue.app.feature.staff.domain.*
import kotlinx.coroutines.*

internal data class StaffAccessUiState(val loading: Boolean = true, val access: StaffAccess? = null, val error: String? = null)
internal class StaffAccessViewModel(private val email: String, private val repository: StaffAccessRepository) : ViewModel() {
    private var generation = 0
    var state by mutableStateOf(StaffAccessUiState())
        private set
    suspend fun refresh() {
        val request = ++generation
        try {
            val access = repository.find(email)
            currentCoroutineContext().ensureActive()
            if (request == generation) state = StaffAccessUiState(loading = false, access = access)
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) {
            if (request == generation) state = StaffAccessUiState(loading = false, error = "Chưa kiểm tra được quyền truy cập. Vui lòng thử lại.")
        }
    }
    fun reload() { viewModelScope.launch { refresh() } }
    fun clear() { generation++; state = StaffAccessUiState() }
}
