package com.foodrescue.app.feature.auth.presentation

enum class AuthPage {
    WELCOME, LOGIN, REGISTER, FORGOT_PASSWORD, REGISTER_OTP, RESET_OTP,
    NEW_PASSWORD, REGISTER_SUCCESS, RESET_SUCCESS,
}

enum class AuthField { NAME, EMAIL, PASSWORD, CONFIRMATION, OTP }

enum class AuthDialog { LOGIN_PREVIEW, REGISTER_PREVIEW, RESET_PREVIEW }

data class AuthFormState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirmation: String = "",
    val otp: String = "",
)

/** Sample code only; no email, credentials or authentication service is involved. */
data class AuthOtpState(
    val sampleCode: String,
    val expiresAtSeconds: Long,
    val resendAtSeconds: Long,
)

data class AuthUiState(
    val page: AuthPage = AuthPage.WELCOME,
    val form: AuthFormState = AuthFormState(),
    val errors: Map<AuthField, Int> = emptyMap(),
    val dialog: AuthDialog? = null,
    val legalDocument: LegalDocument? = null,
    val otp: AuthOtpState? = null,
    val nowSeconds: Long = 0,
    val sampleSequence: Int = 0,
)
