// AC-06,12,13,14,17,24: PasswordRecoveryService responsibility.
package com.foodrescue.backend.auth.service

import com.foodrescue.backend.account.AccountStatus
import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.auth.*
import com.foodrescue.backend.auth.repository.AccountActionTokenRepository
import com.foodrescue.backend.common.security.validatePassword
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.middleware.ratelimit.*
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Clock
@Service
class PasswordRecoveryService(private val accounts: AccountRepository,private val tokens: AccountActionTokenRepository,
    private val credentials: CredentialGenerator,private val normalizer: EmailIdentifierNormalizer,
    private val mail: EmailVerificationService,private val sessions: SessionService,private val encoder: PasswordEncoder,
    private val clock: Clock,private val limits: RateLimitService,private val em: EntityManager) {
    @Transactional fun forgot(email: String) {
        val normalized=normalizer.normalize(email); limits.check(RateLimitPolicy.FORGOT_IDENTIFIER,normalized)
        val a=accounts.findEmailForUpdate(normalized) ?: return
        if(a.status==AccountStatus.ACTIVE && a.emailVerifiedAt!=null) mail.issue(a,TokenPurpose.RESET_PASSWORD)
    }
    @Transactional fun reset(raw: String,password: String) {
        validatePassword(password)
        val token=tokens.findByTokenHash(credentials.hash(raw)) ?: invalid()
        val a=accounts.findForUpdate(token.account.id!!) ?: invalid(); em.refresh(token)
        if(token.purpose!=TokenPurpose.RESET_PASSWORD || token.consumedAt!=null || !token.expiresAt.isAfter(clock.instant()) ||
            a.status!=AccountStatus.ACTIVE || a.emailVerifiedAt==null) invalid()
        token.consumedAt=clock.instant(); a.passwordHash=requireNotNull(encoder.encode(password)); sessions.revokeAll(a,"PASSWORD_RESET")
    }
    private fun invalid(): Nothing = throw ApiException(400,"INVALID_ACTION_TOKEN","Invalid or expired token")
}
