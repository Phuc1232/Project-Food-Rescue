// AC-13,15: StoreAdminQueryService responsibility.
package com.foodrescue.backend.store.service

import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.response.PageResponse
import com.foodrescue.backend.common.validation.PageQuery
import com.foodrescue.backend.store.StoreStatus
import com.foodrescue.backend.store.dto.AdminStoreDetail
import com.foodrescue.backend.store.dto.AdminStoreSummary
import com.foodrescue.backend.store.entity.Store
import com.foodrescue.backend.store.repository.StoreRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** UC-77 steps 1-2: administrators find a store and see its batches, orders and history before deciding. */
@Service
class StoreAdminQueryService(
    private val stores: StoreRepository,
    private val profiles: StoreProfileService,
    private val views: StoreViews,
    private val jdbc: JdbcTemplate
) {
    @Transactional(readOnly = true)
    fun list(status: StoreStatus?, query: String?, page: Int, size: Int): PageResponse<AdminStoreSummary> {
        val pageable = PageQuery().pageable(page, size, "createdAt")
        val name = query?.trim().orEmpty()
        val result = if (status == null) stores.findAllByNameContainingIgnoreCase(name, pageable)
            else stores.findAllByStatusAndNameContainingIgnoreCase(status, name, pageable)
        return PageResponse(result.content.map { AdminStoreSummary(it.id!!, it.name, it.status.name, views.account(it.owner),
            it.statusChangedAt, it.createdAt) }, page, size, result.totalElements, result.totalPages)
    }

    @Transactional(readOnly = true)
    fun detail(storeId: UUID): AdminStoreDetail = detail(stores.findById(storeId).orElse(null)
        ?: throw ApiException(404, "NOT_FOUND", "Resource not found"))

    /** Call inside a transaction. */
    fun detail(store: Store) = AdminStoreDetail(views.profile(store), views.account(store.owner), counts("sale_batch", store.id!!),
        counts("customer_order", store.id!!), profiles.historyPage(store, 0, RECENT_HISTORY, withActorId = true).content)

    private fun counts(table: String, storeId: UUID): Map<String, Long> =
        jdbc.query("SELECT status, count(*) AS n FROM $table WHERE store_id = ? GROUP BY status ORDER BY status", { rs, _ ->
            rs.getString("status") to rs.getLong("n") }, storeId).toMap()

    private companion object { const val RECENT_HISTORY = 50 }
}
