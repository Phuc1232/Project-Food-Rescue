package com.foodrescue.app.feature.surprisebox.domain

internal enum class BatchControl { DRAFT, PUBLISHED, PAUSED, ENDED }
internal enum class BatchStatus { DRAFT, SCHEDULED, SELLING, PAUSED, SOLD_OUT, ENDED }
internal enum class BatchAction { PUBLISH, PAUSE, RESUME, END }
internal enum class BoxField { NAME, DUPLICATE, DESCRIPTION, ALLERGENS, PRICE, VALUE, TEMPLATE, QUANTITY, SALES, PICKUP, LOCKED }

internal data class BoxTemplate(
    val id: String, val name: String, val category: BoxCategory, val description: String,
    val allergens: String, val minimumValue: Long, val price: Long, val active: Boolean = true,
)
internal data class BoxTemplateDraft(
    val id: String? = null, val name: String = "", val category: BoxCategory = BoxCategory.BAKERY,
    val description: String = "", val allergens: String = "", val minimumValue: String = "", val price: String = "",
)
internal fun BoxTemplate.toDraft() = BoxTemplateDraft(id, name, category, description, allergens, minimumValue.toString(), price.toString())

/** Commercial snapshot is immutable after creation; stock counters are supplied by order integration. */
internal data class SaleBatch(
    val id: String, val template: BoxTemplate, val quantity: Int, val salesStart: Long, val salesEnd: Long,
    val pickupStart: Long, val pickupEnd: Long, val control: BatchControl = BatchControl.DRAFT,
    val held: Int = 0, val sold: Int = 0,
) {
    init { require(quantity > 0 && held >= 0 && sold >= 0 && held.toLong() + sold <= quantity) }
    val remaining: Int get() = quantity - held - sold
    val committed: Int get() = held + sold
}
internal data class SaleBatchDraft(
    val id: String? = null, val templateId: String = "", val quantity: String = "",
    val salesStart: Long = 0, val salesEnd: Long = 0, val pickupStart: Long = 0, val pickupEnd: Long = 0,
)
internal fun SaleBatch.toDraft() = SaleBatchDraft(id, template.id, quantity.toString(), salesStart, salesEnd, pickupStart, pickupEnd)
internal data class OwnerBoxCatalog(val templates: List<BoxTemplate> = emptyList(), val batches: List<SaleBatch> = emptyList())
internal class BoxValidationException(val fields: Set<BoxField>) : IllegalArgumentException()
internal interface OwnerBoxRepository {
    suspend fun load(): OwnerBoxCatalog
    suspend fun saveTemplate(draft: BoxTemplateDraft): OwnerBoxCatalog
    suspend fun setTemplateActive(id: String, active: Boolean): OwnerBoxCatalog
    suspend fun saveBatch(draft: SaleBatchDraft): OwnerBoxCatalog
    suspend fun operate(id: String, action: BatchAction): OwnerBoxCatalog
}
