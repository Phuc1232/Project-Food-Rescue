// AC-05,06,16,17,19,23: RegistrationService responsibility.
package com.foodrescue.backend.auth.service

import com.foodrescue.backend.account.*
import com.foodrescue.backend.account.entity.*
import com.foodrescue.backend.account.repository.*
import com.foodrescue.backend.auth.*
import com.foodrescue.backend.auth.dto.*
import com.foodrescue.backend.audit.AuthAuditService
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.security.validatePassword
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.security.crypto.password.PasswordEncoder
@Service
class RegistrationService(private val accounts: AccountRepository,private val roles: AccountRoleRepository,
    private val encoder: PasswordEncoder,private val normalizer: EmailIdentifierNormalizer,
    private val verification: EmailVerificationService,private val audit: AuthAuditService) {
    @Transactional
    fun register(request: RegisterRequest): RegistrationResponse {
        val email=normalizer.normalize(request.email);
        validatePassword(request.password)
        if(accounts.findByLoginIdentifier(email)!=null) throw ApiException(409,"CONFLICT","Account already exists")
        val account=accounts.saveAndFlush(Account(email,requireNotNull(encoder.encode(request.password)),request.displayName.trim(),AccountStatus.PENDING_VERIFICATION))
        roles.save(AccountRole(account,PlatformRole.CUSTOMER));
        verification.issue(account,TokenPurpose.VERIFY_EMAIL)
        audit.record(account,"REGISTERED")
        return RegistrationResponse(account.id!!,account.status.name)
    }
}
