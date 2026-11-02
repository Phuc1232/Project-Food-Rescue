package vn.foodrescue.app.ui.auth

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Credentials live only in memory, never SavedStateHandle, logs or disk. */
class AuthViewModel(private val gateway: AuthGateway, private val clock: () -> Long = { SystemClock.elapsedRealtime() }) : ViewModel() {
    private val mutable = MutableStateFlow(AuthUiState())
    val state = mutable.asStateFlow()
    val isPreview = gateway.isPreview
    private var request: Job? = null
    private var timer: Job? = null
    private var legalReturn = AuthPage.REGISTER

    fun edit(field: AuthFieldKey, value: String) {
        if (mutable.value.busy) return
        val old = mutable.value
        val next = when (field) {
            AuthFieldKey.NAME -> old.copy(name = value)
            AuthFieldKey.EMAIL -> old.copy(email = value, unverified = false)
            AuthFieldKey.PASSWORD -> old.copy(password = value, unverified = false)
            AuthFieldKey.CONFIRMATION -> old.copy(confirmation = value)
            AuthFieldKey.CODE -> old.copy(code = value.filter { it in '0'..'9' }.take(6))
        }.copy(issue = null)
        mutable.value = next.copy(errors = if (next.submitted) AuthValidation.errors(next) else emptyMap())
    }

    fun open(page: AuthPage) {
        if (page !in listOf(AuthPage.WELCOME, AuthPage.REGISTER, AuthPage.LOGIN, AuthPage.FORGOT)) return
        cancelWork()
        mutable.value = AuthUiState(page = page, email = mutable.value.email, name = mutable.value.name,
            scenario = mutable.value.scenario)
    }

    fun legal(page: AuthPage) {
        if (mutable.value.busy || page !in listOf(AuthPage.TERMS, AuthPage.PRIVACY)) return
        legalReturn = mutable.value.page
        mutable.value = mutable.value.copy(page = page)
    }

    fun back() {
        val s = mutable.value
        when (s.page) {
            AuthPage.TERMS, AuthPage.PRIVACY -> mutable.value = s.copy(page = legalReturn)
            AuthPage.VERIFY -> changeEmail()
            AuthPage.NEW_PASSWORD -> open(AuthPage.FORGOT)
            AuthPage.FORGOT, AuthPage.COMPLETE -> open(AuthPage.LOGIN)
            else -> open(AuthPage.WELCOME)
        }
    }

    fun changeEmail() = open(when (mutable.value.purpose) {
        AuthPurpose.REGISTER -> AuthPage.REGISTER
        AuthPurpose.VERIFY_LOGIN -> AuthPage.LOGIN
        AuthPurpose.RESET_PASSWORD -> AuthPage.FORGOT
    })

    fun scenario(value: AuthScenario) {
        if (!isPreview || mutable.value.busy) return
        gateway.scenario = value
        mutable.value = mutable.value.copy(scenario = value, issue = null, unverified = false)
    }

    fun submit() {
        val s = mutable.value
        if (s.busy) return
        val errors = AuthValidation.errors(s)
        mutable.value = s.copy(submitted = true, errors = errors, issue = null)
        if (errors.isNotEmpty()) return
        when (s.page) {
            AuthPage.REGISTER -> runRequest { gateway.register(s.name.trim(), s.email.trim(), s.password) }
            AuthPage.LOGIN -> runRequest { gateway.login(s.email.trim(), s.password) }
            AuthPage.FORGOT -> runRequest { gateway.requestCode(s.email.trim(), AuthPurpose.RESET_PASSWORD) }
            AuthPage.VERIFY -> s.challenge?.let { challenge -> runRequest { gateway.verify(challenge.id, s.code) } }
            AuthPage.NEW_PASSWORD -> {
                val grant = s.resetGrant
                if (grant == null) mutable.value = mutable.value.copy(issue = AuthIssue.CODE_EXPIRED)
                else runRequest { gateway.resetPassword(grant, s.password) }
            }
            else -> Unit
        }
    }

    fun requestVerification() {
        val s = mutable.value
        if (s.unverified && !s.busy && AuthValidation.email(s.email))
            runRequest { gateway.requestCode(s.email.trim(), AuthPurpose.VERIFY_LOGIN) }
    }

    fun resend() {
        val s = mutable.value
        if (s.busy || s.resendIn > 0 || s.page != AuthPage.VERIFY) return
        s.challenge?.let { challenge -> runRequest { gateway.resend(challenge.id) } }
    }

    private fun runRequest(block: suspend () -> AuthReply) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, issue = null)
        request = viewModelScope.launch {
            val reply = try { block() } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { AuthReply.Error(AuthIssue.NETWORK) }
            val s = mutable.value.copy(busy = false)
            mutable.value = when (reply) {
                is AuthReply.Error -> s.copy(issue = reply.issue)
                AuthReply.Unverified -> s.copy(unverified = true)
                is AuthReply.Challenge -> s.copy(page = AuthPage.VERIFY, email = reply.value.email,
                    purpose = reply.value.purpose, challenge = reply.value, resetGrant = null,
                    password = "", confirmation = "", code = "", errors = emptyMap(), submitted = false,
                    unverified = false, previewCode = gateway.previewCode(reply.value.id))
                is AuthReply.Verified -> if (s.purpose == AuthPurpose.RESET_PASSWORD && reply.resetGrant != null)
                    s.copy(page = AuthPage.NEW_PASSWORD, resetGrant = reply.resetGrant, challenge = null,
                        code = "", errors = emptyMap(), submitted = false, previewCode = null)
                else if (s.purpose == AuthPurpose.RESET_PASSWORD) s.copy(issue = AuthIssue.CODE_EXPIRED)
                else completed(s)
                AuthReply.Complete -> completed(s).copy(loginComplete = s.page == AuthPage.LOGIN)
            }
            if (reply is AuthReply.Challenge) startTimer() else if (mutable.value.page != AuthPage.VERIFY) timer?.cancel()
        }
    }

    private fun completed(s: AuthUiState) = s.copy(page = AuthPage.COMPLETE, password = "", confirmation = "",
        code = "", challenge = null, resetGrant = null, previewCode = null, issue = null, errors = emptyMap(), submitted = false)

    private fun startTimer() {
        timer?.cancel()
        timer = viewModelScope.launch {
            while (mutable.value.page == AuthPage.VERIFY) {
                val s = mutable.value
                val challenge = s.challenge ?: break
                val now = clock()
                mutable.value = s.copy(expiresIn = seconds(challenge.expiresAt - now), resendIn = seconds(challenge.resendAt - now))
                delay(250)
            }
        }
    }

    private fun seconds(ms: Long) = ((ms.coerceAtLeast(0) + 999) / 1000).toInt()
    private fun cancelWork() { request?.cancel(); timer?.cancel() }
}
