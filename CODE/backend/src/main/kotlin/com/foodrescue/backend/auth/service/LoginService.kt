// AC-07,08,15: LoginService responsibility.
package com.foodrescue.backend.auth.service

import com.foodrescue.backend.account.AccountStatus
import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.auth.*
import com.foodrescue.backend.auth.entity.LoginSession
import com.foodrescue.backend.auth.dto.*
import com.foodrescue.backend.auth.repository.LoginSessionRepository
import com.foodrescue.backend.audit.AuthAuditService
import com.foodrescue.backend.common.exception.authenticationFailed
import com.foodrescue.backend.common.security.safeMatches
import com.foodrescue.backend.middleware.ratelimit.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Clock
@Service
class LoginService(private val accounts: AccountRepository,private val sessions: LoginSessionRepository,
    private val encoder: PasswordEncoder,private val normalizer: EmailIdentifierNormalizer,private val limits: RateLimitService,
    private val tokens: RefreshTokenService,private val p: AuthProperties,private val clock: Clock,private val audit: AuthAuditService) {
    private val dummy=requireNotNull(encoder.encode("dummy-password-for-timing"))
    @Transactional
    fun login(request: LoginRequest,ip: String): TokenPairResponse {
        val email=normalizer.normalize(request.email)
        limits.check(RateLimitPolicy.LOGIN_IDENTIFIER,email)
        limits.check(RateLimitPolicy.FAILED_IDENTIFIER,email,false); limits.check(RateLimitPolicy.FAILED_IP,ip,false)
        val account=accounts.findEmailForUpdate(email)
        val hash=account?.passwordHash
        val supported=hash?.startsWith("{argon2id}")==true
        val matches=safeMatches(encoder,request.password,if(supported) hash else dummy) && supported
        if(!matches || account.status!=AccountStatus.ACTIVE || account.emailVerifiedAt==null) {
            limits.check(RateLimitPolicy.FAILED_IDENTIFIER,email); limits.check(RateLimitPolicy.FAILED_IP,ip); authenticationFailed()
        }
        val session=sessions.saveAndFlush(LoginSession(account,null,clock.instant().plus(p.sessionTtl)))
        audit.record(account,"LOGIN",session.id!!)
        return tokens.issue(session)
    }
}
