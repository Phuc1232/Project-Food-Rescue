// AC-13,23,24: EmailVerificationService responsibility.
package com.foodrescue.backend.auth.service

import com.foodrescue.backend.account.AccountStatus
import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.auth.*
import com.foodrescue.backend.auth.entity.AccountActionToken
import com.foodrescue.backend.auth.repository.AccountActionTokenRepository
import com.foodrescue.backend.auth.mail.AuthMailSender
import com.foodrescue.backend.audit.AuthAuditService
import com.foodrescue.backend.common.exception.*
import com.foodrescue.backend.middleware.ratelimit.*
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.*
import java.time.Clock
import org.slf4j.LoggerFactory
@Service
class EmailVerificationService(private val accounts: AccountRepository,private val tokens: AccountActionTokenRepository,
    private val credentials: CredentialGenerator,private val clock: Clock,private val properties: AuthProperties,
    private val mail: AuthMailSender,private val normalizer: EmailIdentifierNormalizer,private val limits: RateLimitService,
    private val audit: AuthAuditService,private val em: EntityManager) {
    // Called only inside the account-locked caller transaction (or for a newly inserted account).
    fun issue(account: Account,purpose: TokenPurpose) {
        val now=clock.instant()
        tokens.findAllByAccountIdAndPurposeAndConsumedAtIsNull(account.id!!,purpose).forEach { it.consumedAt=now }
        tokens.flush()
        val raw=credentials.generate()
        val ttl=if(purpose==TokenPurpose.VERIFY_EMAIL) properties.verifyTtl else properties.resetTtl
        tokens.save(AccountActionToken(account,purpose,credentials.hash(raw),now.plus(ttl)))
        val email=account.loginIdentifier
        TransactionSynchronizationManager.registerSynchronization(object: TransactionSynchronization {
            override fun afterCommit() {
                try { mail.send(email,purpose,raw) }
                catch (_: Exception) { LoggerFactory.getLogger(EmailVerificationService::class.java).warn("Auth mail delivery failed; request resend") }
            }
        })
    }
    @Transactional
    fun verify(raw: String) {
        val token=tokens.findByTokenHash(credentials.hash(raw)) ?: invalid()
        val account=accounts.findForUpdate(token.account.id!!) ?: invalid()
        em.refresh(token)
        if(token.purpose!=TokenPurpose.VERIFY_EMAIL || token.consumedAt!=null || !token.expiresAt.isAfter(clock.instant()) ||
            account.status !in setOf(AccountStatus.PENDING_VERIFICATION,AccountStatus.ACTIVE)) invalid()
        token.consumedAt=clock.instant(); account.emailVerifiedAt=clock.instant(); account.status=AccountStatus.ACTIVE
        audit.record(account,"EMAIL_VERIFIED")
    }
    @Transactional
    fun resend(email: String) {
        val normalized=normalizer.normalize(email); limits.check(RateLimitPolicy.RESEND_IDENTIFIER,normalized)
        val a=accounts.findEmailForUpdate(normalized) ?: return
        if(a.emailVerifiedAt==null && a.status in setOf(AccountStatus.ACTIVE,AccountStatus.PENDING_VERIFICATION)) issue(a,TokenPurpose.VERIFY_EMAIL)
    }
    private fun invalid(): Nothing = throw ApiException(400,"INVALID_ACTION_TOKEN","Invalid or expired token")
}
