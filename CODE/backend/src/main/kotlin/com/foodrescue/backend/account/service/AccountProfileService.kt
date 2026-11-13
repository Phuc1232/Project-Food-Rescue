// AC-01,04,05,06,07,08,09: AccountProfileService responsibility.
package com.foodrescue.backend.account.service

import com.foodrescue.backend.account.dto.AccountProfileResponse
import com.foodrescue.backend.account.dto.ProfileUpdateCommand
import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.account.repository.AccountRoleRepository
import com.foodrescue.backend.audit.AuthAuditService
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.exception.authenticationFailed
import com.foodrescue.backend.common.security.StoreAuthorizationService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AccountProfileService(
    private val accounts: AccountRepository,
    private val roles: AccountRoleRepository,
    private val stores: StoreAuthorizationService,
    private val audit: AuthAuditService
) {
    @Transactional(readOnly = true)
    fun get(accountId: UUID): AccountProfileResponse =
        profile(accounts.findById(accountId).orElse(null) ?: authenticationFailed())

    /** Locks the row first so a concurrent request with the same version deterministically gets 409. */
    @Transactional
    fun update(accountId: UUID, command: ProfileUpdateCommand): AccountProfileResponse {
        val account = accounts.findForUpdate(accountId) ?: authenticationFailed()
        if (account.version != command.version)
            throw ApiException(409, "CONFLICT", "Profile was modified; reload and retry")
        if (account.displayName == command.displayName) return profile(account)
        account.displayName = command.displayName
        accounts.saveAndFlush(account)
        audit.record(account, "PROFILE_UPDATED")
        return profile(account)
    }

    private fun profile(account: Account): AccountProfileResponse {
        val id = account.id!!
        return AccountProfileResponse(id, account.loginIdentifier, account.displayName,
            roles.findAllByAccountId(id).map { it.role.name }, stores.contexts(id), account.version)
    }
}
