// AC-07,08 (T4.1): MenuAccess responsibility.
package com.foodrescue.backend.catalog.service

import com.foodrescue.backend.account.PlatformRole
import com.foodrescue.backend.account.repository.AccountRoleRepository
import com.foodrescue.backend.common.security.StoreAuthorizationService
import org.springframework.stereotype.Component
import java.util.UUID

/** Menu is internal (BR-05): owner, staff with MENU_VIEW and administrators read; only the owner writes (MENU_MANAGE is never seeded). */
@Component
class MenuAccess(private val authorization: StoreAuthorizationService, private val roles: AccountRoleRepository) {
    fun requireRead(accountId: UUID, storeId: UUID) {
        if (!roles.existsByAccountIdAndRole(accountId, PlatformRole.ADMIN)) authorization.requirePermission(accountId, storeId, "MENU_VIEW")
    }

    fun requireWrite(accountId: UUID, storeId: UUID) = authorization.requirePermission(accountId, storeId, "MENU_MANAGE")

    /**
     * Non-throwing check for callers inside another transaction (file download): catching an exception thrown by the
     * @Transactional requirePermission would mark the outer transaction rollback-only and turn the request into a 500.
     */
    fun canRead(accountId: UUID, storeId: UUID): Boolean = roles.existsByAccountIdAndRole(accountId, PlatformRole.ADMIN) ||
        authorization.contexts(accountId).any { it.storeId == storeId && (it.owner || "MENU_VIEW" in it.permissions) }
}
