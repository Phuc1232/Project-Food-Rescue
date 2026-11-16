// AC-06 (T4.2): BoxTemplateAccess responsibility.
package com.foodrescue.backend.surprisebox.service

import com.foodrescue.backend.account.PlatformRole
import com.foodrescue.backend.account.repository.AccountRoleRepository
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.security.StoreAuthorizationService
import org.springframework.stereotype.Component
import java.util.UUID

/** Templates: owner writes (BOX_TEMPLATE_MANAGE never seeded); owner, staff with BOX_PACK/BOX_MANAGE and ADMIN read. */
@Component
class BoxTemplateAccess(private val authorization: StoreAuthorizationService, private val roles: AccountRoleRepository) {
    fun requireWrite(accountId: UUID, storeId: UUID) = authorization.requirePermission(accountId, storeId, "BOX_TEMPLATE_MANAGE")

    fun requireRead(accountId: UUID, storeId: UUID) {
        if (roles.existsByAccountIdAndRole(accountId, PlatformRole.ADMIN)) return
        val ok = authorization.contexts(accountId).any { it.storeId == storeId && (it.owner || it.permissions.any { p -> p in READ }) }
        if (!ok) throw ApiException(403, "ACCESS_DENIED", "Access denied")
    }

    private companion object { val READ = setOf("BOX_PACK", "BOX_MANAGE") }
}
