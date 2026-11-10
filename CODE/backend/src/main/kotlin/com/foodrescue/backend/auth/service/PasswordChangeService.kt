// AC-06,12,13,14,17,24: PasswordChangeService responsibility.
package com.foodrescue.backend.auth.service

import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.auth.dto.ChangePasswordRequest
import com.foodrescue.backend.auth.TokenPurpose
import com.foodrescue.backend.auth.repository.AccountActionTokenRepository
import com.foodrescue.backend.common.security.*
import com.foodrescue.backend.common.exception.authenticationFailed
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Clock
@Service
class PasswordChangeService(private val accounts: AccountRepository,private val sessions: SessionService,private val encoder: PasswordEncoder,
    private val tokens: AccountActionTokenRepository,private val clock: Clock) {
    @Transactional fun change(p: AccountPrincipal,request: ChangePasswordRequest) {
        validatePassword(request.newPassword)
        val a=accounts.findForUpdate(p.accountId) ?: authenticationFailed()
        if(!safeMatches(encoder,request.currentPassword,a.passwordHash)) authenticationFailed()
        a.passwordHash=requireNotNull(encoder.encode(request.newPassword))
        tokens.findAllByAccountIdAndPurposeAndConsumedAtIsNull(a.id!!,TokenPurpose.RESET_PASSWORD).forEach { it.consumedAt=clock.instant() }
        sessions.revokeAll(a,"PASSWORD_CHANGED")
    }
}
