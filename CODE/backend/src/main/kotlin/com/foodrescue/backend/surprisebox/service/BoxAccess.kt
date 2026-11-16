// AC-09 (T4.4): BoxAccess responsibility.
package com.foodrescue.backend.surprisebox.service

import com.foodrescue.backend.account.PlatformRole
import com.foodrescue.backend.account.repository.AccountRoleRepository
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.security.StoreAuthorizationService
import org.springframework.stereotype.Component
import java.util.UUID

/** Packing: owner or staff with BOX_PACK (D7). Viewing boxes: owner, staff BOX_PACK/BOX_MANAGE, ADMIN (UC-33). */
@Component
class BoxAccess(private val authorization: StoreAuthorizationService, private val roles: AccountRoleRepository) {
    fun requirePack(accountId: UUID, storeId: UUID) = authorization.requirePermission(accountId, storeId, "BOX_PACK")

    fun requireRead(accountId: UUID, storeId: UUID) {
        if (roles.existsByAccountIdAndRole(accountId, PlatformRole.ADMIN)) return
        if (authorization.contexts(accountId).none { it.storeId == storeId && (it.owner || it.permissions.any { p -> p in READ }) })
            throw ApiException(403, "ACCESS_DENIED", "Access denied")
    }

    private companion object { val READ = setOf("BOX_PACK", "BOX_MANAGE") }
}
