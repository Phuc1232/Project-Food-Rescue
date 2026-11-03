package vn.foodrescue.app.ui.auth

enum class AuthPage { WELCOME, REGISTER, LOGIN, FORGOT, VERIFY, NEW_PASSWORD, COMPLETE, TERMS, PRIVACY }
enum class AuthPurpose { REGISTER, VERIFY_LOGIN, RESET_PASSWORD }
enum class AuthFieldKey { NAME, EMAIL, PASSWORD, CONFIRMATION, CODE }
enum class AuthIssue { NAME, EMAIL, PASSWORD_REQUIRED, PASSWORD_POLICY, CONFIRMATION, CODE_FORMAT,
    CODE_WRONG, CODE_EXPIRED, TOO_MANY_ATTEMPTS, COOLDOWN, CREDENTIALS, NETWORK, EMAIL_USED, MAIL_UNAVAILABLE, UNAVAILABLE }
enum class AuthScenario { SUCCESS, CREDENTIALS, UNVERIFIED, NETWORK, EMAIL_USED }
data class AuthChallenge(val id: String, val email: String, val purpose: AuthPurpose,
    val expiresAt: Long, val resendAt: Long)
sealed interface AuthReply {
    data class Challenge(val value: AuthChallenge) : AuthReply
    data class Verified(val resetGrant: String? = null) : AuthReply
    data object Complete : AuthReply
    data object Unverified : AuthReply
    data class Error(val issue: AuthIssue) : AuthReply
}

/** Real and debug-preview authentication share the same presentation contract. */
interface AuthGateway {
    val isPreview: Boolean get() = false
    var scenario: AuthScenario
    fun previewCode(challengeId: String): String? = null
    suspend fun register(name: String, email: String, password: String): AuthReply
    suspend fun login(email: String, password: String): AuthReply
    suspend fun requestCode(email: String, purpose: AuthPurpose): AuthReply
    suspend fun resend(challengeId: String): AuthReply
    suspend fun verify(challengeId: String, code: String): AuthReply
    suspend fun resetPassword(grant: String, password: String): AuthReply
}

data class AuthUiState(
    val page: AuthPage = AuthPage.WELCOME,
    val name: String = "", val email: String = "", val password: String = "",
    val confirmation: String = "", val code: String = "",
    val busy: Boolean = false, val submitted: Boolean = false,
    val errors: Map<AuthFieldKey, AuthIssue> = emptyMap(), val issue: AuthIssue? = null,
    val unverified: Boolean = false, val challenge: AuthChallenge? = null,
    val resetGrant: String? = null, val purpose: AuthPurpose = AuthPurpose.REGISTER,
    val loginComplete: Boolean = false, val expiresIn: Int = 0, val resendIn: Int = 0,
    val previewCode: String? = null, val scenario: AuthScenario = AuthScenario.SUCCESS,
)

object AuthValidation {
    const val MIN_PASSWORD = 8
    const val MAX_PASSWORD = 128
    private val emailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    fun email(value: String) = value.trim().length in 3..254 && emailPattern.matches(value.trim())
    fun password(value: String) = value.length in MIN_PASSWORD..MAX_PASSWORD && value.isNotBlank()
    fun errors(s: AuthUiState): Map<AuthFieldKey, AuthIssue> = buildMap {
        if (s.page == AuthPage.REGISTER && (s.name.trim().length !in 2..120 || s.name.any(Char::isISOControl)))
            put(AuthFieldKey.NAME, AuthIssue.NAME)
        if (s.page in listOf(AuthPage.REGISTER, AuthPage.LOGIN, AuthPage.FORGOT) && !email(s.email))
            put(AuthFieldKey.EMAIL, AuthIssue.EMAIL)
        if (s.page == AuthPage.LOGIN && s.password.isEmpty()) put(AuthFieldKey.PASSWORD, AuthIssue.PASSWORD_REQUIRED)
        if (s.page in listOf(AuthPage.REGISTER, AuthPage.NEW_PASSWORD)) {
            if (!password(s.password)) put(AuthFieldKey.PASSWORD, AuthIssue.PASSWORD_POLICY)
            if (s.confirmation.isEmpty() || s.confirmation != s.password) put(AuthFieldKey.CONFIRMATION, AuthIssue.CONFIRMATION)
        }
        if (s.page == AuthPage.VERIFY && !s.code.matches(Regex("[0-9]{6}"))) put(AuthFieldKey.CODE, AuthIssue.CODE_FORMAT)
    }
}
