package vn.foodrescue.app.feature.starter.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import vn.foodrescue.app.feature.starter.domain.ConnectionResult
import vn.foodrescue.app.feature.starter.domain.SystemRepository

sealed interface StarterUiState {
    data object Loading : StarterUiState
    data class Ready(val serviceName: String) : StarterUiState
    data class Error(val message: String) : StarterUiState
}
@HiltViewModel
class StarterViewModel @Inject constructor(private val repository: SystemRepository) : ViewModel() {
    private val mutableState = MutableStateFlow<StarterUiState>(StarterUiState.Loading)
    val state = mutableState.asStateFlow()
    private var request: Job? = null
    init { refresh() }
    fun refresh() {
        if (request?.isActive == true) return
        request = viewModelScope.launch {
            mutableState.value = StarterUiState.Loading
            mutableState.value = when (val result = repository.checkConnection()) {
                is ConnectionResult.Connected -> StarterUiState.Ready(result.serviceName)
                is ConnectionResult.Unavailable -> StarterUiState.Error(result.message)
            }
        }
    }
}
