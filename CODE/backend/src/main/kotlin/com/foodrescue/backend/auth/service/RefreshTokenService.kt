// AC-09,21,22: RefreshTokenService responsibility.
package com.foodrescue.backend.auth.service

import com.foodrescue.backend.account.AccountStatus
import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.auth.*
import com.foodrescue.backend.auth.entity.*
import com.foodrescue.backend.auth.dto.TokenPairResponse
import com.foodrescue.backend.auth.repository.*
import com.foodrescue.backend.audit.AuthAuditService
import com.foodrescue.backend.common.security.jwt.JwtTokenIssuer
import com.foodrescue.backend.middleware.ratelimit.*
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
@Service
class RefreshTokenService(private val tokens: RefreshTokenRepository,private val accounts: AccountRepository,
    private val credentials: CredentialGenerator,private val issuer: JwtTokenIssuer,private val clock: Clock,
    private val audit: AuthAuditService,private val limits: RateLimitService,private val em: EntityManager) {
    fun issue(session: LoginSession): TokenPairResponse {
        val raw=credentials.generate(); tokens.saveAndFlush(RefreshToken(session,credentials.hash(raw),session.expiresAt))
        val (jwt,expiry)=issuer.issue(session)
        return TokenPairResponse(jwt,expiry,raw,session.expiresAt)
    }
    // Null is converted to 401 AFTER this transaction commits, preserving reuse revocation.
    @Transactional
    fun refresh(raw: String): TokenPairResponse? {
        val token=tokens.findByTokenHash(credentials.hash(raw)) ?: return null
        val account=accounts.findForUpdate(token.session.account.id!!) ?: return null
        val session=token.session; em.refresh(session); em.refresh(token)
        limits.check(RateLimitPolicy.REFRESH_SESSION,session.id.toString())
        if(token.consumedAt!=null) {
            if(session.revokedAt==null) { session.revokedAt=clock.instant(); audit.record(account,"REFRESH_REUSE",session.id!!) }
            return null
        }
        if(session.revokedAt!=null || !session.expiresAt.isAfter(clock.instant()) || !token.expiresAt.isAfter(clock.instant()) ||
            account.status!=AccountStatus.ACTIVE || account.emailVerifiedAt==null) return null
        token.consumedAt=clock.instant(); tokens.flush()
        val pair=issue(session)
        token.replacedById=tokens.findByTokenHash(credentials.hash(pair.refreshToken))!!.id
        audit.record(account,"REFRESHED",session.id!!)
        return pair
    }
}
