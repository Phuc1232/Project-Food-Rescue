// AC-01,02,06,07,09 (T4.3): SaleBatchService responsibility.
package com.foodrescue.backend.surprisebox.service

import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.catalog.service.PriceValidator
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.exception.authenticationFailed
import com.foodrescue.backend.common.response.FieldViolation
import com.foodrescue.backend.store.repository.StoreRepository
import com.foodrescue.backend.store.service.StoreHistoryRecorder
import com.foodrescue.backend.surprisebox.SaleBatchStatus
import com.foodrescue.backend.surprisebox.dto.*
import com.foodrescue.backend.surprisebox.entity.SaleBatch
import com.foodrescue.backend.surprisebox.repository.BoxTemplateRepository
import com.foodrescue.backend.surprisebox.repository.SaleBatchRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

/** UC-59, UC-60, UC-62. Creating a batch creates no stock (UC-59 A1). */
@Service
class SaleBatchService(
    private val access: SaleBatchAccess,
    private val stores: StoreRepository,
    private val templates: BoxTemplateRepository,
    private val batches: SaleBatchRepository,
    private val accounts: AccountRepository,
    private val prices: PriceValidator,
    private val window: PickupWindowValidator,
    private val availability: BatchAvailability,
    private val history: StoreHistoryRecorder,
    private val views: SaleBatchViews,
    private val jdbc: JdbcTemplate
) {
    @Transactional(readOnly = true)
    fun list(accountId: UUID, storeId: UUID, status: SaleBatchStatus?): List<SaleBatchView> {
        access.requireRead(accountId, storeId)
        return batches.findAllByStoreIdOrderByPickupStartsAtDesc(storeId).filter { status == null || it.status == status }.map(views::view)
    }

    @Transactional(readOnly = true)
    fun get(accountId: UUID, storeId: UUID, batchId: UUID): SaleBatchView { access.requireRead(accountId, storeId); return views.view(find(storeId, batchId)) }

    @Transactional(readOnly = true)
    fun counts(accountId: UUID, storeId: UUID, batchId: UUID): BoxCountsView {
        access.requireRead(accountId, storeId); return availability.counts(find(storeId, batchId).id!!)
    }

    @Transactional
    fun create(ownerId: UUID, storeId: UUID, r: SaleBatchCreateRequest): SaleBatchView {
        access.requireWrite(ownerId, storeId)
        val store = stores.findById(storeId).orElse(null) ?: notFound()
        val template = templates.findById(r.templateId!!).orElse(null)?.takeIf { it.store.id == storeId && it.active }
            ?: throw invalid(listOf(FieldViolation("templateId", "Active template not found")))
        val errors = mutableListOf<FieldViolation>()
        val (selling, minimum) = money(r.sellingPrice ?: template.defaultSellingPrice, r.minimumValue ?: template.defaultMinimumValue, errors)
        errors += window.check(store, r.orderDeadline!!, r.pickupStartsAt!!, r.pickupEndsAt!!, requireFutureDeadline = true)
        if (errors.isNotEmpty()) throw invalid(errors)
        val owner = accounts.findById(ownerId).orElse(null) ?: authenticationFailed()
        val b = batches.saveAndFlush(SaleBatch(store, template, window.saleDate(store, r.pickupStartsAt), selling!!, minimum!!, r.orderDeadline,
            r.pickupStartsAt, r.pickupEndsAt, template.safetyWarnings, SaleBatchStatus.DRAFT))
        history.record(owner, store, ENTITY, b.id!!, "SALE_BATCH_CREATED", null, views.snapshot(b) + mapOf("templateId" to template.id))
        return views.view(b)
    }

    /** UC-60: DRAFT/PAUSED only; with orders only orderDeadline may change (BR-16). Availability is always recomputed (A1). */
    @Transactional
    fun update(ownerId: UUID, storeId: UUID, batchId: UUID, r: SaleBatchUpdateRequest): SaleBatchView {
        access.requireWrite(ownerId, storeId)
        val b = batches.findForUpdate(batchId)?.takeIf { it.store.id == storeId } ?: notFound()
        if (b.version != r.version) throw ApiException(409, "CONFLICT", "Sale batch has changed; reload and try again")
        if (b.status !in EDITABLE) throw ApiException(409, "CONFLICT", "Only DRAFT or PAUSED batches can be edited")
        val termsChanged = listOf(r.sellingPrice, r.minimumValue, r.pickupStartsAt, r.pickupEndsAt, r.safetyWarnings).any { it != null }
        if (termsChanged && hasOrders(b.id!!)) throw ApiException(409, "CONFLICT", "Batch has orders; only orderDeadline can change")
        val errors = mutableListOf<FieldViolation>()
        val warnings = r.safetyWarnings?.trim()?.also { if (it.isEmpty()) errors += FieldViolation("safetyWarnings", "Must not be blank") }
        val (selling, minimum) = money(r.sellingPrice ?: b.sellingPrice, r.minimumValue ?: b.minimumValue, errors)
        val deadline = r.orderDeadline ?: b.orderDeadline; val start = r.pickupStartsAt ?: b.pickupStartsAt; val end = r.pickupEndsAt ?: b.pickupEndsAt
        errors += window.check(b.store, deadline, start, end, requireFutureDeadline = r.orderDeadline != null)
        if (errors.isNotEmpty()) throw invalid(errors)
        val before = views.snapshot(b)
        b.sellingPrice = selling!!; b.minimumValue = minimum!!; b.orderDeadline = deadline; b.pickupStartsAt = start; b.pickupEndsAt = end
        b.saleDate = window.saleDate(b.store, start); warnings?.let { b.safetyWarnings = it }
        val after = views.snapshot(b)
        if (before == after) return views.view(b)
        batches.saveAndFlush(b)
        val owner = accounts.findById(ownerId).orElse(null) ?: authenticationFailed()
        history.record(owner, b.store, ENTITY, b.id!!, "SALE_BATCH_UPDATED", before.filter { (k, v) -> after[k] != v }, after.filter { (k, v) -> before[k] != v })
        return views.view(b)
    }

    private fun hasOrders(batchId: UUID) = jdbc.queryForObject("SELECT count(*) FROM customer_order WHERE sale_batch_id = ?", Long::class.java, batchId)!! > 0

    private fun money(selling: BigDecimal, minimum: BigDecimal, errors: MutableList<FieldViolation>): Pair<BigDecimal?, BigDecimal?> {
        val e1 = prices.check(selling, "sellingPrice"); val e2 = prices.check(minimum, "minimumValue")
        listOfNotNull(e1, e2).forEach(errors::add)
        if (e1 != null || e2 != null) return null to null
        val s = prices.normalize(selling); val m = prices.normalize(minimum)
        if (m < s) { errors += FieldViolation("minimumValue", "Must be at least the selling price"); return null to null }
        return s to m
    }

    private fun find(storeId: UUID, batchId: UUID) = batches.findById(batchId).orElse(null)?.takeIf { it.store.id == storeId } ?: notFound()
    private fun invalid(errors: List<FieldViolation>) = ApiException(400, "VALIDATION_FAILED", "Invalid request", errors)
    private fun notFound(): Nothing = throw ApiException(404, "NOT_FOUND", "Resource not found")

    companion object {
        const val ENTITY = "SALE_BATCH"
        val EDITABLE = setOf(SaleBatchStatus.DRAFT, SaleBatchStatus.PAUSED)
    }
}
