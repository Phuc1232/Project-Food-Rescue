package com.foodrescue.app.feature.surprisebox.domain

import com.foodrescue.app.feature.menu.domain.MenuItem
import com.foodrescue.app.feature.menu.domain.MenuStatus
import com.foodrescue.app.feature.staff.domain.EmployeePermission
import com.foodrescue.app.feature.staff.domain.StaffAccess

internal val SaleBatch.packingTarget: Int get() = if (control == BatchControl.ENDED) sold else quantity
internal fun SaleBatch.canPackAt(now: Long): Boolean = control != BatchControl.DRAFT && now < pickupEnd && prepared < packingTarget

internal fun PackingDraft.packingIssues(access: StaffAccess?, batch: SaleBatch?, menu: List<MenuItem>, now: Long): Set<PackingIssue> = buildSet {
    if (access?.allowed != true || EmployeePermission.PREPARE_BOXES !in access.permissions) add(PackingIssue.ACCESS)
    if (batch == null || batch.id != batchId || batch.control == BatchControl.DRAFT || now >= batch.pickupEnd ||
        (batch.control == BatchControl.ENDED && batch.sold == 0)) add(PackingIssue.BATCH)
    if (batch != null && batch.prepared >= batch.packingTarget) add(PackingIssue.CAPACITY)
    val current = menu.associateBy { it.id }
    if (selections.any { selected -> current[selected.itemId].let { item -> item == null || item.status != MenuStatus.ACTIVE ||
            item.price != selected.unitPrice || item.unit != selected.unit || item.updatedAtMillis != selected.updatedAt } }) add(PackingIssue.MENU_CHANGED)
    val estimate = estimateBoxValue(menu, selections.associate { it.itemId to it.quantity })
    if (!estimate.canApply || selections.map { it.itemId }.distinct().size != selections.size ||
        estimate.lineValues.size != selections.size || id.isBlank()) add(PackingIssue.ITEMS)
    if (batch != null && estimate.total < batch.template.minimumValue) add(PackingIssue.VALUE)
    if (expiresAt <= now || batch == null || expiresAt < batch.pickupEnd) add(PackingIssue.EXPIRY)
    if (storage == null) add(PackingIssue.STORAGE)
    if (!foodChecked || !packagingChecked || !allergensChecked) add(PackingIssue.INSPECTION)
}

internal fun PackingDraft.completePacking(access: StaffAccess, batch: SaleBatch, menu: List<MenuItem>, now: Long): PackedBox {
    val issues = packingIssues(access, batch, menu, now)
    if (issues.isNotEmpty()) throw PackingException(issues)
    val estimate = estimateBoxValue(menu, selections.associate { it.itemId to it.quantity })
    val items = selections.map { selection ->
        val item = menu.first { it.id == selection.itemId }
        PackedItem(item.id, item.name, item.unit, item.price, selection.quantity.trim().replace(',', '.'), estimate.lineValues.getValue(item.id))
    }
    return PackedBox(id, access.employee.storeId, batch.id, batch.template.name, batch.template.minimumValue,
        batch.template.allergens, items, expiresAt, checkNotNull(storage), access.employee.id, access.employee.name, now)
}
