package vn.foodrescue.app.ui.auth

import android.os.SystemClock
import kotlinx.coroutines.delay

fun createAuthGateway(): AuthGateway = PreviewAuthGateway()

/** Debug-only simulator. No email, account, password or session is persisted. */
class PreviewAuthGateway(private val clock: () -> Long = { SystemClock.elapsedRealtime() },
    private val latency: Long = 700) : AuthGateway {
    override val isPreview = true
    override var scenario = AuthScenario.SUCCESS
    private data class Entry(val challenge: AuthChallenge, val code: String, var attempts: Int = 0)
    private val challenges = mutableMapOf<String, Entry>()
    private val grants = mutableMapOf<String, Long>()
    private var serial = 0
    override fun previewCode(challengeId: String) = challenges[challengeId]?.code

    override suspend fun register(name: String, email: String, password: String): AuthReply {
        delay(latency)
        if (scenario == AuthScenario.NETWORK) return AuthReply.Error(AuthIssue.NETWORK)
        if (scenario == AuthScenario.EMAIL_USED) return AuthReply.Error(AuthIssue.EMAIL_USED)
        return issue(email, AuthPurpose.REGISTER)
    }
    override suspend fun login(email: String, password: String): AuthReply {
        delay(latency)
        return when (scenario) {
            AuthScenario.NETWORK -> AuthReply.Error(AuthIssue.NETWORK)
            AuthScenario.CREDENTIALS -> AuthReply.Error(AuthIssue.CREDENTIALS)
            AuthScenario.UNVERIFIED -> AuthReply.Unverified
            else -> AuthReply.Complete
        }
    }
    override suspend fun requestCode(email: String, purpose: AuthPurpose): AuthReply {
        delay(latency)
        if (scenario == AuthScenario.NETWORK) return AuthReply.Error(AuthIssue.NETWORK)
        return issue(email, purpose)
    }
    private fun issue(email: String, purpose: AuthPurpose): AuthReply {
        // A reissue invalidates prior codes for this email and purpose.
        challenges.entries.removeAll { it.value.challenge.email == email && it.value.challenge.purpose == purpose }
        serial++
        val id = "preview-$serial"
        val challenge = AuthChallenge(id, email, purpose, clock() + 180_000, clock() + 30_000)
        challenges[id] = Entry(challenge, (246809 + serial).toString().takeLast(6).padStart(6, '0'))
        return AuthReply.Challenge(challenge)
    }
    override suspend fun resend(challengeId: String): AuthReply {
        delay(latency)
        if (scenario == AuthScenario.NETWORK) return AuthReply.Error(AuthIssue.NETWORK)
        val entry = challenges[challengeId] ?: return AuthReply.Error(AuthIssue.CODE_EXPIRED)
        if (clock() < entry.challenge.resendAt) return AuthReply.Error(AuthIssue.COOLDOWN)
        challenges.remove(challengeId)
        return issue(entry.challenge.email, entry.challenge.purpose)
    }
    override suspend fun verify(challengeId: String, code: String): AuthReply {
        delay(latency)
        if (scenario == AuthScenario.NETWORK) return AuthReply.Error(AuthIssue.NETWORK)
        val entry = challenges[challengeId] ?: return AuthReply.Error(AuthIssue.CODE_EXPIRED)
        if (clock() >= entry.challenge.expiresAt) return AuthReply.Error(AuthIssue.CODE_EXPIRED)
        if (entry.attempts >= 5) return AuthReply.Error(AuthIssue.TOO_MANY_ATTEMPTS)
        if (code != entry.code) {
            entry.attempts++
            return AuthReply.Error(if (entry.attempts >= 5) AuthIssue.TOO_MANY_ATTEMPTS else AuthIssue.CODE_WRONG)
        }
        challenges.remove(challengeId)
        if (entry.challenge.purpose != AuthPurpose.RESET_PASSWORD) return AuthReply.Verified()
        val grant = "reset-$challengeId"
        grants[grant] = clock() + 300_000
        return AuthReply.Verified(grant)
    }
    override suspend fun resetPassword(grant: String, password: String): AuthReply {
        delay(latency)
        if (scenario == AuthScenario.NETWORK) return AuthReply.Error(AuthIssue.NETWORK)
        val expiry = grants[grant] ?: return AuthReply.Error(AuthIssue.CODE_EXPIRED)
        if (clock() >= expiry) return AuthReply.Error(AuthIssue.CODE_EXPIRED)
        if (!AuthValidation.password(password)) return AuthReply.Error(AuthIssue.PASSWORD_POLICY)
        grants.remove(grant)
        return AuthReply.Complete
    }
}
