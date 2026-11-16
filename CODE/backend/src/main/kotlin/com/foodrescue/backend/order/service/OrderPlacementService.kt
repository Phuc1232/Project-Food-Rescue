// AC-01..08 (T5.2): OrderPlacementService responsibility.
package com.foodrescue.backend.order.service

import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.exception.authenticationFailed
import com.foodrescue.backend.common.response.FieldViolation
import com.foodrescue.backend.finance.service.FinancialPolicyResolver
import com.foodrescue.backend.finance.service.MoneyRounding
import com.foodrescue.backend.order.AllocationStatus
import com.foodrescue.backend.order.OrderProperties
import com.foodrescue.backend.order.OrderStatus
import com.foodrescue.backend.order.dto.*
import com.foodrescue.backend.order.entity.BoxAllocation
import com.foodrescue.backend.order.entity.CustomerOrder
import com.foodrescue.backend.order.repository.BoxAllocationRepository
import com.foodrescue.backend.order.repository.CustomerOrderRepository
import com.foodrescue.backend.search.service.OfferQueryService
import com.foodrescue.backend.store.service.StoreHistoryRecorder
import com.foodrescue.backend.surprisebox.BoxStatus
import com.foodrescue.backend.surprisebox.repository.PhysicalBoxRepository
import com.foodrescue.backend.surprisebox.repository.SaleBatchRepository
import com.foodrescue.backend.surprisebox.service.BatchAvailability
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.util.UUID

data class PlaceResult(val order: OrderView, val created: Boolean)

/**
 * UC-13 steps 1-6. Placing holds exactly N real boxes or nothing (E1, BR-27) in one transaction. Lock order: customer account (same customer,
 * idempotency) -> store and batch FOR SHARE (a concurrent suspension or pause waits) -> boxes FOR UPDATE SKIP LOCKED.
 */
@Service
class OrderPlacementService(
    private val accounts: AccountRepository,
    private val orders: CustomerOrderRepository,
    private val allocations: BoxAllocationRepository,
    private val boxes: PhysicalBoxRepository,
    private val batches: SaleBatchRepository,
    private val offers: OfferQueryService,
    private val policies: FinancialPolicyResolver,
    private val codes: OrderCodeGenerator,
    private val history: StoreHistoryRecorder,
    private val views: OrderViews,
    private val properties: OrderProperties,
    private val jdbc: NamedParameterJdbcTemplate,
    private val clock: Clock
) {
    @Transactional(readOnly = true)
    fun preview(customerId: UUID, r: OrderPreviewRequest): OrderPreview {
        checkQuantity(r.quantity!!)
        val offer = offers.detail(r.batchId!!)
        if (offer.storeId == ownerStoreOf(customerId, offer.storeId)) throw ApiException(409, "OWN_STORE", "You cannot order from your own store")
        offer.unavailableReason?.let { throw unavailable(it, offer.available) }
        if (offer.available < r.quantity) throw notEnough(offer.available)
        val policy = policies.current()
        val total = offer.sellingPrice * BigDecimal(r.quantity)
        return OrderPreview(offer.batchId, offer.storeName, offer.boxName, r.quantity, offer.available, offer.sellingPrice, total, offer.minimumValue,
            offer.safetyWarnings, offer.address, offer.pickupStartsAt, offer.pickupEndsAt, properties.holdMinutes,
            PolicySummary(policy.policyVersion, policy.noShowRetentionRate.stripTrailingZeros(), MoneyRounding.applyRate(total, policy.noShowRetentionRate),
                policy.disputeWindowHours, policy.cancellationTerms, policy.refundTerms))
    }

    @Transactional
    fun place(customerId: UUID, idempotencyKey: String?, r: OrderCreateRequest): PlaceResult {
        val key = idempotencyKey?.trim()?.takeIf { it.matches(KEY) }
            ?: throw ApiException(400, "VALIDATION_FAILED", "Invalid request", listOf(FieldViolation("Idempotency-Key", "Required, 1-64 of [A-Za-z0-9_-]")))
        checkQuantity(r.quantity!!)
        val customer = accounts.findForUpdate(customerId) ?: authenticationFailed()
        orders.findByCustomerIdAndIdempotencyKey(customerId, key)?.let { existing ->
            if (existing.saleBatch.id != r.batchId || existing.quantity != r.quantity)
                throw ApiException(409, "IDEMPOTENCY_KEY_REUSED", "Idempotency-Key was used for a different order")
            return PlaceResult(views.view(existing), false)
        }
        val now = clock.instant()
        val p = MapSqlParameterSource("id", r.batchId).addValue("now", Timestamp.from(now)).addValue("n", r.quantity)
        val row = jdbc.query("""SELECT s.status AS store_status, s.owner_id, b.status AS batch_status, b.order_deadline FROM sale_batch b
            JOIN store s ON s.id = b.store_id WHERE b.id = :id FOR SHARE OF s, b""", p) { rs, _ ->
            arrayOf(rs.getString(1), rs.getObject(2, UUID::class.java), rs.getString(3), rs.getTimestamp(4).toInstant()) }.firstOrNull()
            ?: throw ApiException(404, "NOT_FOUND", "Resource not found")
        if (row[1] == customerId) throw ApiException(409, "OWN_STORE", "You cannot order from your own store")
        if (row[0] != "ACTIVE") throw ApiException(409, "STORE_NOT_ACTIVE", "Store is not active")
        if (row[2] != "OPEN") throw unavailable("PAUSED", 0)
        if (!(row[3] as java.time.Instant).isAfter(now)) throw unavailable("DEADLINE_PASSED", 0)
        val policy = policies.at(now)
        if (policy.policyVersion != r.acceptedPolicyVersion)
            throw ApiException(409, "POLICY_CHANGED", "The financial policy changed; review the order again",
                listOf(FieldViolation("acceptedPolicyVersion", policy.policyVersion.toString())))

        val boxIds = jdbc.queryForList("""SELECT p.id FROM physical_box p JOIN sale_batch b ON b.id = p.current_batch_id
            WHERE p.current_batch_id = :id AND p.status = 'AVAILABLE' AND ${BatchAvailability.MEETS_TERMS} AND ${BatchAvailability.NOT_HELD}
            ORDER BY p.safe_until, p.packed_at, p.id LIMIT :n FOR UPDATE OF p SKIP LOCKED""", p, UUID::class.java).filterNotNull()
        if (boxIds.size < r.quantity) throw notEnough(boxIds.size.toLong())

        val batch = batches.findById(r.batchId!!).orElseThrow()
        val held = boxes.findAllById(boxIds)
        val itemWarnings = jdbc.queryForList("SELECT DISTINCT safety_warnings_snapshot FROM box_item WHERE box_id IN (:ids) ORDER BY 1",
            MapSqlParameterSource("ids", boxIds), String::class.java).filterNotNull().map { it.trim() }.filter { it.isNotEmpty() }
        val warnings = (listOf(batch.safetyWarnings.trim()) + itemWarnings).distinct().joinToString("; ")
        val holdUntil = now.plus(Duration.ofMinutes(properties.holdMinutes))
        val order = orders.saveAndFlush(CustomerOrder(customer, batch.store, batch, codes.next(), r.quantity, OrderStatus.PENDING_PAYMENT, holdUntil,
            batch.sellingPrice, batch.sellingPrice * BigDecimal(r.quantity), batch.minimumValue, "VND", warnings, batch.store.address,
            batch.pickupStartsAt, batch.pickupEndsAt, policy, policy.commissionRate, policy.noShowRetentionRate, policy.disputeWindowHours,
            policy.cancellationTerms, policy.refundTerms, now, idempotencyKey = key))
        allocations.saveAll(held.map { BoxAllocation(batch.store, order, it, batch, AllocationStatus.HELD, now, holdUntil) })
        held.forEach { it.status = BoxStatus.RESERVED }
        boxes.saveAllAndFlush(held)
        allocations.flush()
        history.record(customer, batch.store, ENTITY, order.id!!, "ORDER_CREATED", null,
            mapOf("orderCode" to order.orderCode, "quantity" to order.quantity, "boxes" to held.map { it.boxCode }, "holdExpiresAt" to holdUntil.toString()))
        return PlaceResult(views.view(order), true)
    }

    @Transactional(readOnly = true)
    fun get(customerId: UUID, orderId: UUID): OrderView =
        views.view(orders.findById(orderId).orElse(null)?.takeIf { it.customer.id == customerId } ?: throw ApiException(404, "NOT_FOUND", "Resource not found"))

    private fun ownerStoreOf(customerId: UUID, storeId: UUID): UUID? = jdbc.queryForList("SELECT id FROM store WHERE id = :s AND owner_id = :c",
        MapSqlParameterSource("s", storeId).addValue("c", customerId), UUID::class.java).firstOrNull()

    private fun checkQuantity(q: Int) {
        if (q !in 1..properties.maxQuantity)
            throw ApiException(400, "VALIDATION_FAILED", "Invalid request", listOf(FieldViolation("quantity", "Must be between 1 and ${properties.maxQuantity}")))
    }

    private fun notEnough(available: Long) = ApiException(409, "NOT_ENOUGH_BOXES", "Not enough boxes available",
        listOf(FieldViolation("quantity", available.toString())))

    private fun unavailable(reason: String, available: Long) = when (reason) {
        "DEADLINE_PASSED" -> ApiException(409, "ORDER_DEADLINE_PASSED", "Order deadline has passed")
        "SOLD_OUT" -> notEnough(available)
        else -> ApiException(409, "BATCH_NOT_OPEN", "Sale batch is not open")
    }

    companion object {
        const val ENTITY = "CUSTOMER_ORDER"
        val KEY = Regex("^[A-Za-z0-9_-]{1,64}$")
    }
}
