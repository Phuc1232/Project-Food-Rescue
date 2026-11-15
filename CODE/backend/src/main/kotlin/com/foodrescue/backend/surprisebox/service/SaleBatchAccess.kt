// AC-09 (T4.3): SaleBatchAccess responsibility.
package com.foodrescue.backend.surprisebox.service

import com.foodrescue.backend.account.PlatformRole
import com.foodrescue.backend.account.repository.AccountRoleRepository
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.security.StoreAuthorizationService
import org.springframework.stereotype.Component
import java.util.UUID

/** Batches: owner writes (SALE_BATCH_MANAGE never seeded, BR-10); owner, store staff with an operational permission and ADMIN read. */
@Component
class SaleBatchAccess(private val authorization: StoreAuthorizationService, private val roles: AccountRoleRepository) {
    fun requireWrite(accountId: UUID, storeId: UUID) = authorization.requirePermission(accountId, storeId, "SALE_BATCH_MANAGE")

    fun requireRead(accountId: UUID, storeId: UUID) {
        if (roles.existsByAccountIdAndRole(accountId, PlatformRole.ADMIN)) return
        if (authorization.contexts(accountId).none { it.storeId == storeId && (it.owner || it.permissions.any { p -> p in READ }) })
            throw ApiException(403, "ACCESS_DENIED", "Access denied")
    }

    private companion object { val READ = setOf("BOX_PACK", "BOX_MANAGE", "PICKUP_HANDLE", "ORDER_VIEW") }
}
