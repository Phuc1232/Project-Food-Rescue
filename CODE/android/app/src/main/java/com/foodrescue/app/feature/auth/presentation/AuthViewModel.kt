package com.foodrescue.app.feature.auth.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.foodrescue.app.R

/** UI preview only. Form values stay in memory; this class does not authenticate or send email. */
class AuthViewModel : ViewModel() {
    var uiState by mutableStateOf(AuthUiState())
        private set

    fun navigate(page: AuthPage) {
        uiState = uiState.copy(
            page = page,
            form = uiState.form.copy(password = "", confirmation = ""),
            errors = emptyMap(),
            dialog = null,
            legalDocument = null,
        )
    }

    fun back() {
        if (uiState.legalDocument != null) uiState = uiState.copy(legalDocument = null)
        else if (uiState.dialog != null) dismissDialog()
        else navigate(if (uiState.page == AuthPage.FORGOT_PASSWORD) AuthPage.LOGIN else AuthPage.WELCOME)
    }

    fun edit(field: AuthField, value: String) {
        val form = when (field) {
            AuthField.NAME -> uiState.form.copy(name = value)
            AuthField.EMAIL -> uiState.form.copy(email = value)
            AuthField.PASSWORD -> uiState.form.copy(password = value)
            AuthField.CONFIRMATION -> uiState.form.copy(confirmation = value)
        }
        val errors = if (field == AuthField.PASSWORD) {
            uiState.errors - field - AuthField.CONFIRMATION
        } else uiState.errors - field
        uiState = uiState.copy(form = form, errors = errors)
    }

    fun submit() {
        val page = uiState.page
        if (page == AuthPage.WELCOME) return
        val form = uiState.form
        val errors = buildMap {
            if (page == AuthPage.REGISTER && form.name.isBlank()) {
                put(AuthField.NAME, R.string.auth_error_name)
            }
            if (!EMAIL_PATTERN.matches(form.email.trim())) {
                put(AuthField.EMAIL, R.string.auth_error_email)
            }
            if (page != AuthPage.FORGOT_PASSWORD) {
                when {
                    form.password.isBlank() -> put(AuthField.PASSWORD, R.string.auth_error_password_required)
                    page == AuthPage.REGISTER && form.password.length < 8 ->
                        put(AuthField.PASSWORD, R.string.auth_error_password_short)
                    form.password.length > 128 -> put(AuthField.PASSWORD, R.string.auth_error_password_long)
                }
            }
            if (page == AuthPage.REGISTER &&
                (form.confirmation.isEmpty() || form.confirmation != form.password)
            ) {
                put(AuthField.CONFIRMATION, R.string.auth_error_confirmation)
            }
        }
        uiState = uiState.copy(
            errors = errors,
            dialog = if (errors.isNotEmpty()) null else when (page) {
                AuthPage.LOGIN -> AuthDialog.LOGIN_PREVIEW
                AuthPage.REGISTER -> AuthDialog.REGISTER_PREVIEW
                AuthPage.FORGOT_PASSWORD -> AuthDialog.RESET_PREVIEW
                AuthPage.WELCOME -> null
            },
        )
    }

    fun openLegal(document: LegalDocument) {
        uiState = uiState.copy(legalDocument = document, dialog = null)
    }

    fun dismissDialog() { uiState = uiState.copy(dialog = null) }

    private companion object {
        val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}
