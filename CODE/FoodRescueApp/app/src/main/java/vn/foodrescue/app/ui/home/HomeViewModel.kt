package vn.foodrescue.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import vn.foodrescue.app.data.repository.ConnectionResult
import vn.foodrescue.app.data.repository.SystemRepository

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Ready(val serviceName: String) : HomeUiState
    data class Error(val message: String) : HomeUiState
}
@HiltViewModel
class HomeViewModel @Inject constructor(private val repository: SystemRepository) : ViewModel() {
    private val mutableState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state = mutableState.asStateFlow()
    private var request: Job? = null
    init { refresh() }
    fun refresh() {
        if (request?.isActive == true) return
        request = viewModelScope.launch {
            mutableState.value = HomeUiState.Loading
            mutableState.value = when (val result = repository.checkConnection()) {
                is ConnectionResult.Connected -> HomeUiState.Ready(result.serviceName)
                is ConnectionResult.Unavailable -> HomeUiState.Error(result.message)
            }
        }
    }
}
