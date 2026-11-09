package com.foodrescue.app.feature.auth.presentation

import com.foodrescue.app.R

/** Deterministic, in-memory preview transitions. Never stores or verifies real credentials. */
internal object AuthFlow {
    private val emailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun edit(state: AuthUiState, field: AuthField, value: String): AuthUiState {
        val form = when (field) {
            AuthField.NAME -> state.form.copy(name = value)
            AuthField.EMAIL -> state.form.copy(email = value)
            AuthField.PASSWORD -> state.form.copy(password = value)
            AuthField.CONFIRMATION -> state.form.copy(confirmation = value)
            AuthField.OTP -> state.form.copy(otp = value.filter { it in '0'..'9' }.take(6))
        }
        return state.copy(form = form, errors = if (field == AuthField.PASSWORD) {
            state.errors - field - AuthField.CONFIRMATION
        } else state.errors - field)
    }

    fun submit(state: AuthUiState, nowSeconds: Long): AuthUiState {
        if (state.page == AuthPage.REGISTER_OTP || state.page == AuthPage.RESET_OTP) {
            return verifyOtp(state, nowSeconds)
        }
        if (state.page !in setOf(AuthPage.LOGIN, AuthPage.REGISTER, AuthPage.FORGOT_PASSWORD, AuthPage.NEW_PASSWORD)) return state
        val form = state.form
        val creatingPassword = state.page == AuthPage.REGISTER || state.page == AuthPage.NEW_PASSWORD
        val errors = buildMap {
            if (state.page == AuthPage.REGISTER && form.name.isBlank()) put(AuthField.NAME, R.string.auth_error_name)
            if (state.page != AuthPage.NEW_PASSWORD && !emailPattern.matches(form.email.trim())) {
                put(AuthField.EMAIL, R.string.auth_error_email)
            }
            if (state.page != AuthPage.FORGOT_PASSWORD) {
                when {
                    form.password.isBlank() -> put(AuthField.PASSWORD, R.string.auth_error_password_required)
                    creatingPassword && form.password.length < 8 -> put(AuthField.PASSWORD, R.string.auth_error_password_short)
                    form.password.length > 128 -> put(AuthField.PASSWORD, R.string.auth_error_password_long)
                }
            }
            if (creatingPassword && (form.confirmation.isEmpty() || form.confirmation != form.password)) {
                put(AuthField.CONFIRMATION, R.string.auth_error_confirmation)
            }
        }
        val valid = state.copy(errors = errors, dialog = null, form = form.copy(email = form.email.trim()))
        if (errors.isNotEmpty()) return valid
        return when (state.page) {
            AuthPage.LOGIN -> valid.copy(dialog = AuthDialog.LOGIN_PREVIEW)
            AuthPage.REGISTER -> issueOtp(valid.copy(page = AuthPage.REGISTER_OTP), nowSeconds)
            AuthPage.FORGOT_PASSWORD -> issueOtp(valid.copy(page = AuthPage.RESET_OTP), nowSeconds)
            AuthPage.NEW_PASSWORD -> valid.copy(page = AuthPage.RESET_SUCCESS, form = clearSecrets(valid.form), otp = null)
            else -> valid
        }
    }

    fun resend(state: AuthUiState, nowSeconds: Long): AuthUiState {
        if (state.page != AuthPage.REGISTER_OTP && state.page != AuthPage.RESET_OTP) return state
        if (nowSeconds < (state.otp?.resendAtSeconds ?: Long.MAX_VALUE)) return state
        return issueOtp(state, nowSeconds)
    }

    private fun issueOtp(state: AuthUiState, nowSeconds: Long): AuthUiState {
        val sequence = state.sampleSequence + 1
        val code = (123455L + sequence).toString().takeLast(6).padStart(6, '0')
        return state.copy(
            otp = AuthOtpState(code, nowSeconds + 120, nowSeconds + 30),
            nowSeconds = nowSeconds, sampleSequence = sequence,
            form = state.form.copy(otp = ""), errors = emptyMap(),
        )
    }

    private fun verifyOtp(state: AuthUiState, nowSeconds: Long): AuthUiState {
        val otp = state.otp
        val error = when {
            otp == null || nowSeconds >= otp.expiresAtSeconds -> R.string.auth_error_otp_expired
            state.form.otp.length != 6 -> R.string.auth_error_otp_length
            state.form.otp != otp.sampleCode -> R.string.auth_error_otp_incorrect
            else -> null
        }
        if (error != null) return state.copy(errors = mapOf(AuthField.OTP to error), nowSeconds = nowSeconds)
        return state.copy(
            page = if (state.page == AuthPage.REGISTER_OTP) AuthPage.REGISTER_SUCCESS else AuthPage.NEW_PASSWORD,
            form = clearSecrets(state.form), errors = emptyMap(), otp = null,
        )
    }

    fun navigate(state: AuthUiState, page: AuthPage): AuthUiState = state.copy(
        page = page, form = clearSecrets(state.form), errors = emptyMap(),
        dialog = null, legalDocument = null, otp = null,
    )

    fun back(state: AuthUiState): AuthUiState = when {
        state.legalDocument != null -> state.copy(legalDocument = null)
        state.dialog != null -> state.copy(dialog = null)
        state.page == AuthPage.REGISTER_OTP -> state.copy(page = AuthPage.REGISTER, otp = null, errors = emptyMap(), form = state.form.copy(otp = ""))
        state.page == AuthPage.RESET_OTP -> navigate(state, AuthPage.FORGOT_PASSWORD)
        state.page in setOf(AuthPage.FORGOT_PASSWORD, AuthPage.NEW_PASSWORD, AuthPage.REGISTER_SUCCESS, AuthPage.RESET_SUCCESS) -> navigate(state, AuthPage.LOGIN)
        else -> navigate(state, AuthPage.WELCOME)
    }

    fun clearSecrets(form: AuthFormState): AuthFormState = form.copy(password = "", confirmation = "", otp = "")
}
