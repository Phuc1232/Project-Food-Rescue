package com.foodrescue.app.feature.account.presentation

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import com.foodrescue.app.feature.account.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class AccountAccessState(val loading: Boolean = true, val allowed: Boolean = false,
    val reason: String? = null, val expired: Boolean = false, val error: Boolean = false)
internal class AccountAccessViewModel(private val repository: AccountSessionRepository) : ViewModel() {
    var state by mutableStateOf(AccountAccessState())
        private set
    fun checking() { state = AccountAccessState() }
    suspend fun refresh(email: String, version: Long) {
        try {
            val record = repository.access(email); currentCoroutineContext().ensureActive()
            state = when {
                record.status == AccountStatus.SUSPENDED -> AccountAccessState(false, reason = record.events.last().reason)
                record.profile == null || record.accessVersion != version -> AccountAccessState(false, expired = true)
                else -> AccountAccessState(false, allowed = true)
            }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { state = AccountAccessState(false, error = true) }
    }
}

