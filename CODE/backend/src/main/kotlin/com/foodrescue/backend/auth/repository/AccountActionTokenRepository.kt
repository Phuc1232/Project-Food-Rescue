// AC-14,18,21,23: AccountActionTokenRepository responsibility.
package com.foodrescue.backend.auth.repository

import com.foodrescue.backend.auth.entity.AccountActionToken
import com.foodrescue.backend.auth.TokenPurpose
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID
interface AccountActionTokenRepository : JpaRepository<AccountActionToken,UUID> {
    fun findByTokenHash(tokenHash: String): AccountActionToken?
    fun findAllByAccountIdAndPurposeAndConsumedAtIsNull(accountId: UUID,purpose: TokenPurpose): List<AccountActionToken>
}
