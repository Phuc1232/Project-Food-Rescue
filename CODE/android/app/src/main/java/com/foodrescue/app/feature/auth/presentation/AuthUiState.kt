package com.foodrescue.app.feature.auth.presentation

enum class AuthPage { WELCOME, LOGIN, REGISTER, FORGOT_PASSWORD }

enum class AuthField { NAME, EMAIL, PASSWORD, CONFIRMATION }

enum class AuthDialog { LOGIN_PREVIEW, REGISTER_PREVIEW, RESET_PREVIEW }

data class AuthFormState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirmation: String = "",
)

data class AuthUiState(
    val page: AuthPage = AuthPage.WELCOME,
    val form: AuthFormState = AuthFormState(),
    val errors: Map<AuthField, Int> = emptyMap(),
    val dialog: AuthDialog? = null,
    val legalDocument: LegalDocument? = null,
)
