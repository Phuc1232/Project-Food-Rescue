package vn.foodrescue.service

import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import vn.foodrescue.dto.*
import vn.foodrescue.exception.ApiException
import vn.foodrescue.repository.*
import java.time.Clock
import java.time.Duration
import java.util.Locale
import java.util.UUID

@Service
@Transactional(noRollbackFor = [ApiException::class])
class AuthService(private val repository: AuthRepository, private val passwords: PasswordEncoder,
    private val tokens: TokenService, private val mail: VerificationMailSender,
    private val clock: Clock) {
    // Same expensive password verification for an unknown account. Never log raw credentials.
    private val dummyHash = passwords.encode(tokens.randomToken())

    fun register(request: RegisterRequest): ChallengeResponse {
        val email = normalize(request.email)
        val name = validName(request.name)
        val account = repository.insertAccount(email, name, passwords.encode(request.password), clock.instant())
            ?: throw ApiException(HttpStatus.CONFLICT, "EMAIL_USED", "Email đã được sử dụng. Hãy đăng nhập hoặc khôi phục mật khẩu.")
        return issue(account, email, "VERIFY_EMAIL")
    }

    fun requestCode(emailValue: String, purpose: String): ChallengeResponse {
        val email = normalize(emailValue)
        val account = repository.accountByEmail(email, lock = true)?.takeIf {
            if (purpose == "VERIFY_EMAIL") it.status == "CHO_XAC_THUC" else it.status == "HOAT_DONG"
        }
        repository.latestChallenge(email, purpose)?.let { cooldown(it) }
        // A real-looking challenge for unknown/ineligible emails avoids disclosing their existence.
        return issue(account, email, purpose)
    }

    fun resend(id: String): ChallengeResponse {
        val before = repository.challenge(id) ?: expired()
        val account = before.accountId?.let { repository.account(it, lock = true) }
        val challenge = repository.challenge(id, lock = true) ?: expired()
        if (challenge.usedAt != null) expired()
        cooldown(challenge)
        var code = tokens.code()
        while (tokens.matches(challenge.codeHash, tokens.keyedHash("otp:$id:$code"))) code = tokens.code()
        repository.renewChallenge(id, tokens.keyedHash("otp:$id:$code"), clock.instant())
        if (eligible(account, challenge.purpose)) mail.send(challenge.email, code, challenge.purpose)
        return response(repository.challenge(id)!!)
    }

    fun verify(id: String, code: String): VerificationResponse {
        val before = repository.challenge(id) ?: expired()
        val account = before.accountId?.let { repository.account(it, lock = true) }
        val challenge = repository.challenge(id, lock = true) ?: expired()
        if (challenge.usedAt != null || !clock.instant().isBefore(challenge.expiresAt)) expired()
        if (challenge.attempts >= 5) throw ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS", "Đã nhập sai mã 5 lần. Hãy yêu cầu mã mới.")
        if (!tokens.matches(challenge.codeHash, tokens.keyedHash("otp:$id:$code")) || !eligible(account, challenge.purpose)) {
            repository.wrongCode(id)
            throw ApiException(HttpStatus.BAD_REQUEST, "CODE_WRONG", "Mã xác minh chưa đúng.")
        }
        repository.useChallenge(id, clock.instant())
        if (challenge.purpose == "VERIFY_EMAIL") {
            repository.activate(account!!.id)
            return VerificationResponse()
        }
        val grant = tokens.randomToken()
        repository.insertGrant(account!!.id, tokens.hash(grant), clock.instant())
        return VerificationResponse(resetGrant = grant)
    }

    fun login(request: LoginRequest): SessionResponse {
        val email = normalize(request.email)
        val account = repository.accountByEmail(email, lock = true)
        val matches = passwords.matches(request.password, account?.passwordHash ?: dummyHash)
        if (!matches || account == null || account.status == "BI_KHOA") unauthorized("CREDENTIALS", "Email hoặc mật khẩu chưa đúng.")
        if (account.status != "HOAT_DONG") throw ApiException(HttpStatus.FORBIDDEN, "EMAIL_UNVERIFIED", "Hãy xác minh email trước khi đăng nhập.")
        return session(account, UUID.randomUUID().toString(), clock.instant().plusSeconds(7 * 86400))
    }

    fun refresh(refreshToken: String): SessionResponse {
        val hash = tokens.hash(refreshToken)
        val before = repository.sessionByHash(hash) ?: unauthorized()
        val account = repository.account(before.accountId, lock = true) ?: unauthorized()
        val previous = repository.sessionByHash(hash, lock = true) ?: unauthorized()
        if (previous.revokedAt != null) {
            repository.revokeFamily(previous.family, clock.instant())
            unauthorized()
        }
        if (account.status != "HOAT_DONG" || !clock.instant().isBefore(previous.expiresAt)) unauthorized()
        repository.revokeSession(previous.id, clock.instant())
        // Absolute seven-day lifetime is not extended on rotation.
        return session(account, previous.family, previous.expiresAt)
    }

    fun logout(principal: AuthPrincipal) {
        repository.account(principal.accountId, lock = true) ?: unauthorized()
        repository.session(principal.sessionId)?.let { repository.revokeFamily(it.family, clock.instant()) }
    }

    fun resetPassword(request: ResetPasswordRequest) {
        val hash = tokens.hash(request.resetGrant)
        val before = repository.grant(hash) ?: expired()
        val account = repository.account(before.accountId, lock = true) ?: expired()
        val grant = repository.grant(hash, lock = true) ?: expired()
        if (grant.usedAt != null || !clock.instant().isBefore(grant.expiresAt) || account.status != "HOAT_DONG") expired()
        repository.updatePassword(account.id, passwords.encode(request.password))
        repository.invalidateGrants(account.id, clock.instant())
        repository.invalidateChallenges(account.id, "RESET_PASSWORD", clock.instant())
        repository.revokeAll(account.id, clock.instant())
    }

    fun profile(principal: AuthPrincipal): ProfileResponse = activeAccount(principal).profile()
    fun updateProfile(principal: AuthPrincipal, request: ProfileRequest): ProfileResponse {
        val account = activeAccount(principal, lock = true)
        repository.updateProfile(account.id, validName(request.name), request.phone.ifBlank { null })
        return repository.account(account.id)!!.profile()
    }

    private fun activeAccount(principal: AuthPrincipal, lock: Boolean = false): Account {
        val account = repository.account(principal.accountId, lock) ?: unauthorized()
        val session = repository.session(principal.sessionId) ?: unauthorized()
        if (account.status != "HOAT_DONG" || session.revokedAt != null || !clock.instant().isBefore(session.expiresAt)) unauthorized()
        return account
    }
    private fun issue(account: Account?, email: String, purpose: String): ChallengeResponse {
        val id = UUID.randomUUID().toString(); val code = tokens.code(); val now = clock.instant()
        account?.let { repository.invalidateChallenges(it.id, purpose, now) }
        repository.insertChallenge(id, account?.id, email, purpose, tokens.keyedHash("otp:$id:$code"), now)
        if (account != null) mail.send(email, code, purpose)
        return response(repository.challenge(id)!!)
    }
    private fun session(account: Account, family: String, expiresAt: java.time.Instant): SessionResponse {
        val refresh = tokens.randomToken()
        val id = repository.insertSession(account.id, tokens.hash(refresh), family, clock.instant(), expiresAt)
        return SessionResponse(tokens.access(account.id, id), refresh, tokens.accessLifetime, account.profile())
    }
    private fun eligible(account: Account?, purpose: String) = account != null &&
        account.status == if (purpose == "VERIFY_EMAIL") "CHO_XAC_THUC" else "HOAT_DONG"
    private fun response(c: Challenge) = ChallengeResponse(c.id, c.email, c.purpose,
        Duration.between(clock.instant(), c.expiresAt).seconds.coerceAtLeast(0),
        Duration.between(clock.instant(), c.resendAt).seconds.coerceAtLeast(0))
    private fun cooldown(c: Challenge) {
        if (clock.instant().isBefore(c.resendAt)) throw ApiException(HttpStatus.TOO_MANY_REQUESTS, "COOLDOWN",
            "Vui lòng chờ một phút trước khi yêu cầu mã mới.", Duration.between(clock.instant(), c.resendAt).seconds + 1)
    }
    private fun normalize(email: String) = email.trim().lowercase(Locale.ROOT)
    private fun validName(name: String): String = name.trim().also {
        if (it.length !in 2..120 || it.any(Char::isISOControl))
            throw ApiException(HttpStatus.BAD_REQUEST, "NAME_INVALID", "Họ tên cần từ 2 đến 120 ký tự.")
    }
    private fun expired(): Nothing = throw ApiException(HttpStatus.GONE, "CODE_EXPIRED", "Mã đã hết hạn hoặc đã được sử dụng. Hãy yêu cầu mã mới.")
    private fun unauthorized(code: String = "SESSION_EXPIRED", message: String = "Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại."): Nothing =
        throw ApiException(HttpStatus.UNAUTHORIZED, code, message)
}

data class AuthPrincipal(val accountId: Int, val sessionId: Int)
