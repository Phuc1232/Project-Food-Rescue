package com.foodrescue.app.feature.surprisebox.domain

import java.text.Normalizer
import java.util.Locale

private fun String.nameKey() = Normalizer.normalize(trim().lowercase(Locale.ROOT), Normalizer.Form.NFC).replace(Regex("\\s+"), " ")
internal fun String.boxSearchKey() = Normalizer.normalize(lowercase(Locale.ROOT), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").replace('đ', 'd').trim()
private fun String.amount() = trim().takeIf { it.matches(Regex("[0-9]{1,9}")) }?.toLongOrNull()

internal fun BoxTemplateDraft.validationErrors(templates: List<BoxTemplate>): Set<BoxField> = buildSet {
    if (name.trim().length !in 2..80) add(BoxField.NAME)
    if (templates.any { it.id != id && it.name.nameKey() == name.nameKey() }) add(BoxField.DUPLICATE)
    if (description.trim().length !in 10..500) add(BoxField.DESCRIPTION)
    if (allergens.trim().length !in 2..250) add(BoxField.ALLERGENS)
    val value = minimumValue.amount()
    val sellingPrice = price.amount()
    if (value == null || value !in 2..999_999_999) add(BoxField.VALUE)
    if (sellingPrice == null || sellingPrice < 1 || (value != null && sellingPrice >= value)) add(BoxField.PRICE)
}

internal fun SaleBatch.statusAt(now: Long): BatchStatus = when {
    control == BatchControl.ENDED -> BatchStatus.ENDED
    control == BatchControl.DRAFT -> BatchStatus.DRAFT
    now >= salesEnd -> BatchStatus.ENDED
    control == BatchControl.PAUSED -> BatchStatus.PAUSED
    remaining == 0 -> BatchStatus.SOLD_OUT
    now < salesStart -> BatchStatus.SCHEDULED
    else -> BatchStatus.SELLING
}

internal fun SaleBatch.actionsAt(now: Long): Set<BatchAction> = when (statusAt(now)) {
    BatchStatus.DRAFT -> if (now < salesEnd) setOf(BatchAction.PUBLISH, BatchAction.END) else setOf(BatchAction.END)
    BatchStatus.SCHEDULED, BatchStatus.SELLING -> setOf(BatchAction.PAUSE, BatchAction.END)
    BatchStatus.PAUSED -> if (remaining > 0) setOf(BatchAction.RESUME, BatchAction.END) else setOf(BatchAction.END)
    BatchStatus.SOLD_OUT -> setOf(BatchAction.END)
    BatchStatus.ENDED -> emptySet()
}

internal fun SaleBatchDraft.validationErrors(catalog: OwnerBoxCatalog, now: Long): Set<BoxField> = buildSet {
    val old = catalog.batches.firstOrNull { it.id == id }
    if (id != null && old == null) add(BoxField.LOCKED)
    if (old?.statusAt(now) == BatchStatus.ENDED) add(BoxField.LOCKED)
    val sameTemplate = old?.template?.id == templateId
    if (!sameTemplate && catalog.templates.none { it.id == templateId && it.active }) add(BoxField.TEMPLATE)
    val count = quantity.takeIf { it.matches(Regex("[0-9]{1,5}")) }?.toIntOrNull()
    if (count == null || count !in 1..9999 || count < (old?.committed ?: 0)) add(BoxField.QUANTITY)
    if (salesStart <= 0 || salesStart >= salesEnd || salesEnd <= now) add(BoxField.SALES)
    if (pickupStart < salesEnd || pickupEnd <= pickupStart) add(BoxField.PICKUP)
    // A published start time describes history; only a draft may move it.
    if (old != null && old.control != BatchControl.DRAFT && (templateId != old.template.id || salesStart != old.salesStart)) add(BoxField.LOCKED)
    if (old != null && old.committed > 0 && (templateId != old.template.id || salesStart != old.salesStart ||
            salesEnd != old.salesEnd || pickupStart != old.pickupStart || pickupEnd != old.pickupEnd)) add(BoxField.LOCKED)
}

internal fun OwnerBoxCatalog.saveTemplate(draft: BoxTemplateDraft, newId: String): OwnerBoxCatalog {
    val errors = draft.validationErrors(templates)
    if (errors.isNotEmpty()) throw BoxValidationException(errors)
    val previous = draft.id?.let { id -> templates.firstOrNull { it.id == id } ?: error("Missing template") }
    val item = BoxTemplate(previous?.id ?: newId, draft.name.trim(), draft.category, draft.description.trim(), draft.allergens.trim(),
        draft.minimumValue.trim().toLong(), draft.price.trim().toLong(), previous?.active ?: true)
    return copy(templates = if (previous == null) templates + item else templates.map { if (it.id == item.id) item else it })
}

internal fun OwnerBoxCatalog.saveBatch(draft: SaleBatchDraft, now: Long, newId: String): OwnerBoxCatalog {
    val errors = draft.validationErrors(this, now)
    if (errors.isNotEmpty()) throw BoxValidationException(errors)
    val previous = batches.firstOrNull { it.id == draft.id }
    val snapshot = previous?.template?.takeIf { it.id == draft.templateId } ?: templates.first { it.id == draft.templateId }
    val batch = SaleBatch(previous?.id ?: newId, snapshot, draft.quantity.toInt(), draft.salesStart, draft.salesEnd,
        draft.pickupStart, draft.pickupEnd, previous?.control ?: BatchControl.DRAFT, previous?.held ?: 0, previous?.sold ?: 0)
    return copy(batches = if (previous == null) batches + batch else batches.map { if (it.id == batch.id) batch else it })
}

internal fun OwnerBoxCatalog.operate(id: String, action: BatchAction, now: Long): OwnerBoxCatalog {
    val batch = batches.first { it.id == id }
    check(action in batch.actionsAt(now)) { "Sale action no longer available" }
    if (action == BatchAction.PUBLISH) check(templates.any { it.id == batch.template.id && it.active }) { "Template is inactive" }
    val control = when (action) {
        BatchAction.PUBLISH, BatchAction.RESUME -> BatchControl.PUBLISHED
        BatchAction.PAUSE -> BatchControl.PAUSED
        BatchAction.END -> BatchControl.ENDED
    }
    return copy(batches = batches.map { if (it.id == id) it.copy(control = control) else it })
}
