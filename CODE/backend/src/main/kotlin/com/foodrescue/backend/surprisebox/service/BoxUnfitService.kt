// AC-01..12 (T7.5): BoxUnfitService responsibility.
package com.foodrescue.backend.surprisebox.service

import com.foodrescue.backend.account.PlatformRole
import com.foodrescue.backend.account.repository.AccountRoleRepository
import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.security.StoreAuthorizationService
import com.foodrescue.backend.order.repository.CustomerOrderRepository
import com.foodrescue.backend.order.service.PaidOrderCancellationExecutor
import com.foodrescue.backend.surprisebox.dto.*
import com.foodrescue.backend.surprisebox.repository.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

@Service
class BoxUnfitService(private val authorization: StoreAuthorizationService, private val roles: AccountRoleRepository,
    private val accounts: AccountRepository, private val orders: CustomerOrderRepository, private val boxes: PhysicalBoxRepository,
    private val batches: SaleBatchRepository, private val processor: BoxUnfitProcessor, private val cancellation: PaidOrderCancellationExecutor,
    private val jdbc: JdbcTemplate, tm: PlatformTransactionManager) {
    private val transactions = TransactionTemplate(tm)
    private class AllocationChanged : RuntimeException()
    fun markStoreBoxUnfit(actorId: UUID, storeId: UUID, boxId: UUID, r: BoxUnfitRequest, key: String?): BoxImpactResultView {
        authorization.requirePermission(actorId, storeId, "BOX_MANAGE")
        return run(actorId, storeId, boxId, r, key, "STORE_BOX_UNFIT")
    }
    fun blockBoxByAdmin(actorId: UUID, boxId: UUID, r: BoxUnfitRequest, key: String?): BoxImpactResultView {
        if (!roles.existsByAccountIdAndRole(actorId, PlatformRole.ADMIN)) throw ApiException(403, "ACCESS_DENIED", "Administrator role required")
        return run(actorId, null, boxId, r, key, "ADMIN_BOX_BLOCK")
    }
    private fun run(actorId: UUID, storeId: UUID?, boxId: UUID, r: BoxUnfitRequest, key: String?, operation: String): BoxImpactResultView {
        cancellation.validateKey(key)
        repeat(3) {
            try { return transactions.execute {
                val row = jdbc.queryForList("select store_id,current_batch_id from physical_box where id=?", boxId).firstOrNull() ?: missing()
                if (storeId != null && row["store_id"] != storeId) missing()
                batches.findForShare(row["current_batch_id"] as UUID) ?: missing()
                val candidates = candidateIds(boxId)
                val lockedOrders = candidates.map { orders.findForUpdate(it) ?: missing() }
                val box = boxes.findForUpdate(boxId) ?: missing()
                if (box.currentBatch.id != row["current_batch_id"] || !candidates.containsAll(candidateIds(boxId))) throw AllocationChanged()
                processor.process(box, accounts.getReferenceById(actorId), r, lockedOrders, operation, key)
            } } catch (_: AllocationChanged) { /* roll back and retry with the complete order set */ }
        }
        throw ApiException(409, "CONCURRENT_MODIFICATION", "Box allocations changed; retry the request")
    }
    private fun candidateIds(boxId: UUID) = jdbc.queryForList("select distinct order_id from box_allocation where box_id=? and status in ('HELD','CONFIRMED','DELIVERED') order by order_id", UUID::class.java, boxId).filterNotNull()
    private fun missing(): Nothing = throw ApiException(404, "NOT_FOUND", "Box or batch not found")
}
