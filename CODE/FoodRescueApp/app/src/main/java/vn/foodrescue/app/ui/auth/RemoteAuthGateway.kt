package vn.foodrescue.app.ui.auth

import android.os.SystemClock
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import vn.foodrescue.app.data.remote.*
import vn.foodrescue.app.data.session.SessionRepository
import java.io.IOException

class RemoteAuthGateway(private val sessions: SessionRepository,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() }) : AuthGateway {
    override var scenario = AuthScenario.SUCCESS // Preview controls are never rendered for this gateway.
    private val api get() = sessions.api
    private var purpose = AuthPurpose.REGISTER

    override suspend fun register(name: String, email: String, password: String): AuthReply = request {
        purpose = AuthPurpose.REGISTER
        challenge(api.register(RegisterBody(name.trim(), email.trim(), password)))
    }
    override suspend fun login(email: String, password: String): AuthReply = request {
        sessions.login(email, password)
        AuthReply.Complete
    }
    override suspend fun requestCode(email: String, purpose: AuthPurpose): AuthReply = request {
        this.purpose = purpose
        challenge(if (purpose == AuthPurpose.RESET_PASSWORD) api.passwordResetRequest(EmailBody(email.trim()))
            else api.emailVerification(EmailBody(email.trim())))
    }
    override suspend fun resend(challengeId: String): AuthReply = request { challenge(api.resend(challengeId)) }
    override suspend fun verify(challengeId: String, code: String): AuthReply = request {
        val response = api.verify(challengeId, VerifyBody(code))
        if (response.verified) AuthReply.Verified(response.resetGrant) else AuthReply.Error(AuthIssue.CODE_WRONG)
    }
    override suspend fun resetPassword(grant: String, password: String): AuthReply = request {
        api.resetPassword(ResetPasswordBody(grant, password)); AuthReply.Complete
    }
    private fun challenge(result: ChallengeResult): AuthReply.Challenge {
        val now = clock()
        // Server sends durations. A wall-clock timestamp must never be compared with elapsedRealtime().
        return AuthReply.Challenge(AuthChallenge(result.id, result.email, purpose,
            now + result.expiresInSeconds.coerceAtLeast(0) * 1000, now + result.resendInSeconds.coerceAtLeast(0) * 1000))
    }
    private suspend fun request(block: suspend () -> AuthReply): AuthReply = try { block() }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (error: HttpException) {
        val code = runCatching { Gson().fromJson(error.response()?.errorBody()?.string(), RemoteError::class.java)?.code }.getOrNull()
        if (code == "EMAIL_UNVERIFIED") AuthReply.Unverified else AuthReply.Error(when (code) {
            "EMAIL_USED" -> AuthIssue.EMAIL_USED
            "CREDENTIALS" -> AuthIssue.CREDENTIALS
            "CODE_WRONG" -> AuthIssue.CODE_WRONG
            "CODE_EXPIRED" -> AuthIssue.CODE_EXPIRED
            "TOO_MANY_ATTEMPTS" -> AuthIssue.TOO_MANY_ATTEMPTS
            "COOLDOWN" -> AuthIssue.COOLDOWN
            "NAME_INVALID" -> AuthIssue.NAME
            "MAIL_UNAVAILABLE" -> AuthIssue.MAIL_UNAVAILABLE
            else -> AuthIssue.UNAVAILABLE
        })
    } catch (_: IOException) { AuthReply.Error(AuthIssue.NETWORK) }
}
