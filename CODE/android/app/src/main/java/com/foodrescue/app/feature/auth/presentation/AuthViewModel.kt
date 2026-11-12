package com.foodrescue.app.feature.auth.presentation

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/** UI preview only. Values live in memory; nothing authenticates, sends email or saves passwords. */
class AuthViewModel : ViewModel() {
    var uiState by mutableStateOf(AuthUiState())
        private set

    fun navigate(page: AuthPage) { uiState = AuthFlow.navigate(uiState, page) }
    fun back() { uiState = AuthFlow.back(uiState) }
    fun edit(field: AuthField, value: String) { uiState = AuthFlow.edit(uiState, field, value) }
    fun submit() { uiState = AuthFlow.submit(uiState, nowSeconds()) }
    fun resend() { uiState = AuthFlow.resend(uiState, nowSeconds()) }
    fun tick() { uiState = uiState.copy(nowSeconds = nowSeconds()) }

    fun openLegal(document: LegalDocument) {
        uiState = uiState.copy(legalDocument = document, dialog = null)
    }

    fun dismissDialog() { uiState = uiState.copy(dialog = null) }

    fun reset() { uiState = AuthUiState() }

    fun enterPreview(onAuthenticated: (String, String) -> Unit) {
        if (uiState.dialog != AuthDialog.LOGIN_PREVIEW) return
        val name = uiState.form.name.trim().ifBlank { uiState.form.email.substringBefore('@') }
        val email = uiState.form.email.trim()
        uiState = uiState.copy(dialog = null, form = AuthFlow.clearSecrets(uiState.form))
        onAuthenticated(name, email)
    }

    private fun nowSeconds(): Long = SystemClock.elapsedRealtime() / 1_000
}
