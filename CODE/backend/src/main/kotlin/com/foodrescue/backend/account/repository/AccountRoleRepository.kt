// AC-10,16: persistence support for verified accounts and controlled sessions.
package com.foodrescue.backend.account.repository

import com.foodrescue.backend.account.entity.AccountRole
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AccountRoleRepository : JpaRepository<AccountRole, UUID> {
    fun findAllByAccountId(accountId: UUID): List<AccountRole>
    fun existsByAccountIdAndRole(accountId: UUID,role: com.foodrescue.backend.account.PlatformRole): Boolean
}
