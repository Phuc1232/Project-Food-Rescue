// AC-01..06 (T4.2): BoxTemplateService responsibility.
package com.foodrescue.backend.surprisebox.service

import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.catalog.entity.FoodCategory
import com.foodrescue.backend.catalog.repository.FoodCategoryRepository
import com.foodrescue.backend.catalog.service.PriceValidator
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.exception.authenticationFailed
import com.foodrescue.backend.common.response.FieldViolation
import com.foodrescue.backend.file.entity.StoredFile
import com.foodrescue.backend.file.repository.StoredFileRepository
import com.foodrescue.backend.store.repository.StoreRepository
import com.foodrescue.backend.store.service.StoreHistoryRecorder
import com.foodrescue.backend.surprisebox.dto.*
import com.foodrescue.backend.surprisebox.entity.BoxTemplate
import com.foodrescue.backend.surprisebox.repository.BoxTemplateRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

/** UC-57/UC-58. A template never creates batches or boxes; batches snapshot warnings and prices (BR-09). */
@Service
class BoxTemplateService(
    private val access: BoxTemplateAccess,
    private val stores: StoreRepository,
    private val templates: BoxTemplateRepository,
    private val categories: FoodCategoryRepository,
    private val files: StoredFileRepository,
    private val accounts: AccountRepository,
    private val prices: PriceValidator,
    private val history: StoreHistoryRecorder
) {
    @Transactional(readOnly = true)
    fun list(accountId: UUID, storeId: UUID, active: Boolean?): List<BoxTemplateView> {
        access.requireRead(accountId, storeId)
        return templates.findAllByStoreIdOrderByName(storeId).filter { active == null || it.active == active }.map(::view)
    }

    @Transactional(readOnly = true)
    fun get(accountId: UUID, storeId: UUID, templateId: UUID): BoxTemplateView {
        access.requireRead(accountId, storeId)
        return view(templates.findById(templateId).orElse(null)?.takeIf { it.store.id == storeId } ?: notFound())
    }

    @Transactional
    fun create(ownerId: UUID, storeId: UUID, r: BoxTemplateCreateRequest): BoxTemplateView {
        access.requireWrite(ownerId, storeId)
        val errors = mutableListOf<FieldViolation>()
        val category = category(r.categoryCode!!, errors)
        val (selling, minimum) = prices(r.defaultSellingPrice!!, r.defaultMinimumValue!!, errors)
        val cover = r.coverFileId?.let { cover(it, ownerId, errors) }
        if (errors.isNotEmpty()) throw invalid(errors)
        val store = stores.findById(storeId).orElse(null) ?: notFound()
        val owner = accounts.findById(ownerId).orElse(null) ?: authenticationFailed()
        val t = templates.saveAndFlush(BoxTemplate(store, category!!, r.name!!.trim(), r.description!!.trim(), r.safetyWarnings!!.trim(),
            selling!!, minimum!!, cover, true))
        history.record(owner, store, ENTITY, t.id!!, "BOX_TEMPLATE_CREATED", null, snapshot(t))
        return view(t)
    }

    /** Row lock + version: concurrent edits of one version give one 200 and one 409. */
    @Transactional
    fun update(ownerId: UUID, storeId: UUID, templateId: UUID, r: BoxTemplateUpdateRequest): BoxTemplateView {
        access.requireWrite(ownerId, storeId)
        val t = locked(storeId, templateId, r.version!!)
        val errors = mutableListOf<FieldViolation>()
        fun text(v: String?, field: String) = v?.trim()?.also { if (it.isEmpty()) errors += FieldViolation(field, "Must not be blank") }
        val name = text(r.name, "name"); val description = text(r.description, "description"); val warnings = text(r.safetyWarnings, "safetyWarnings")
        val category = r.categoryCode?.let { category(it, errors) }
        val (selling, minimum) = prices(r.defaultSellingPrice ?: t.defaultSellingPrice, r.defaultMinimumValue ?: t.defaultMinimumValue, errors)
        val cover = r.coverFileId?.let { cover(it, ownerId, errors) }
        if (errors.isNotEmpty()) throw invalid(errors)
        val before = snapshot(t)
        name?.let { t.name = it }; description?.let { t.description = it }; warnings?.let { t.safetyWarnings = it }
        category?.let { t.category = it }; t.defaultSellingPrice = selling!!; t.defaultMinimumValue = minimum!!; cover?.let { t.coverFile = it }
        val after = snapshot(t)
        if (before == after) return view(t)
        templates.saveAndFlush(t)
        val owner = accounts.findById(ownerId).orElse(null) ?: authenticationFailed()
        history.record(owner, t.store, ENTITY, t.id!!, "BOX_TEMPLATE_UPDATED", before.filter { (k, v) -> after[k] != v },
            after.filter { (k, v) -> before[k] != v })
        return view(t)
    }

    @Transactional
    fun changeStatus(ownerId: UUID, storeId: UUID, templateId: UUID, r: BoxTemplateStatusRequest): BoxTemplateView {
        access.requireWrite(ownerId, storeId)
        val t = locked(storeId, templateId, r.version!!)
        if (t.active == r.active) throw ApiException(409, "CONFLICT", "Template is already ${if (t.active) "active" else "inactive"}")
        if (r.active == true && !t.category.active)
            throw invalid(listOf(FieldViolation("active", "Template category is inactive")))
        t.active = r.active!!
        templates.saveAndFlush(t)
        val owner = accounts.findById(ownerId).orElse(null) ?: authenticationFailed()
        history.record(owner, t.store, ENTITY, t.id!!, "BOX_TEMPLATE_STATUS_CHANGED", mapOf("active" to !t.active),
            mapOf("active" to t.active, "reason" to r.reason!!.trim()))
        return view(t)
    }

    private fun prices(selling: BigDecimal, minimum: BigDecimal, errors: MutableList<FieldViolation>): Pair<BigDecimal?, BigDecimal?> {
        val e1 = prices.check(selling, "defaultSellingPrice"); val e2 = prices.check(minimum, "defaultMinimumValue")
        listOfNotNull(e1, e2).forEach(errors::add)
        if (e1 != null || e2 != null) return null to null
        val s = prices.normalize(selling); val m = prices.normalize(minimum)
        if (m < s) { errors += FieldViolation("defaultMinimumValue", "Must be at least the selling price"); return null to null }
        return s to m
    }

    private fun cover(fileId: UUID, ownerId: UUID, errors: MutableList<FieldViolation>): StoredFile? =
        files.findById(fileId).orElse(null)?.takeIf { it.uploadedBy.id == ownerId && it.mimeType.startsWith("image/") }
            ?: run { errors += FieldViolation("coverFileId", "Image file not found"); null }

    private fun category(code: String, errors: MutableList<FieldViolation>): FoodCategory? =
        categories.findByCode(code.trim())?.takeIf { it.active } ?: run { errors += FieldViolation("categoryCode", "Unknown category"); null }

    private fun locked(storeId: UUID, id: UUID, version: Long): BoxTemplate {
        val t = templates.findForUpdate(id)?.takeIf { it.store.id == storeId } ?: notFound()
        if (t.version != version) throw ApiException(409, "CONFLICT", "Template has changed; reload and try again")
        return t
    }

    private fun snapshot(t: BoxTemplate): Map<String, Any?> = linkedMapOf("name" to t.name, "categoryCode" to t.category.code,
        "description" to t.description, "safetyWarnings" to t.safetyWarnings, "defaultSellingPrice" to t.defaultSellingPrice.setScale(0),
        "defaultMinimumValue" to t.defaultMinimumValue.setScale(0), "coverFileId" to t.coverFile?.id, "active" to t.active)

    fun view(t: BoxTemplate) = BoxTemplateView(t.id!!, t.name, t.category.code, t.category.name, t.description, t.safetyWarnings,
        t.defaultSellingPrice.setScale(0), t.defaultMinimumValue.setScale(0), t.coverFile?.id, t.active, t.version)

    private fun invalid(errors: List<FieldViolation>) = ApiException(400, "VALIDATION_FAILED", "Invalid request", errors)
    private fun notFound(): Nothing = throw ApiException(404, "NOT_FOUND", "Resource not found")

    companion object { const val ENTITY = "BOX_TEMPLATE" }
}
