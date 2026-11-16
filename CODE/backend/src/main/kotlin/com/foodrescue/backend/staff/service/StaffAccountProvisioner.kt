// AC-02,05 (T3.4): StaffAccountProvisioner responsibility.
package com.foodrescue.backend.staff.service

import com.foodrescue.backend.account.AccountStatus
import com.foodrescue.backend.account.PlatformRole
import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.account.entity.AccountRole
import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.account.repository.AccountRoleRepository
import com.foodrescue.backend.auth.CredentialGenerator
import com.foodrescue.backend.auth.TokenPurpose
import com.foodrescue.backend.auth.service.EmailVerificationService
import com.foodrescue.backend.common.exception.ApiException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

/**
 * D01 for a new email: an account nobody can log into (random password, PENDING_VERIFICATION) plus a SET_PASSWORD link sent
 * after commit through the existing auth mail; using the link proves the email and activates the account (T3.4 Q3).
 */
@Component
class StaffAccountProvisioner(
    private val accounts: AccountRepository,
    private val roles: AccountRoleRepository,
    private val encoder: PasswordEncoder,
    private val credentials: CredentialGenerator,
    private val mail: EmailVerificationService
) {
    fun create(email: String, displayName: String): Account {
        val account = try {
            accounts.saveAndFlush(Account(email, requireNotNull(encoder.encode(credentials.generate())), displayName,
                AccountStatus.PENDING_VERIFICATION))
        } catch (_: DataIntegrityViolationException) {
            throw ApiException(409, "CONFLICT", "Account was just created; try again")
        }
        roles.save(AccountRole(account, PlatformRole.CUSTOMER))
        mail.issue(account, TokenPurpose.SET_PASSWORD)
        return account
    }
}
