// AC-01..06,09 (T4.4): BoxPackingService responsibility.
package com.foodrescue.backend.surprisebox.service

import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.catalog.MenuItemStatus
import com.foodrescue.backend.catalog.repository.MenuItemRepository
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.exception.authenticationFailed
import com.foodrescue.backend.common.response.FieldViolation
import com.foodrescue.backend.store.service.StoreHistoryRecorder
import com.foodrescue.backend.surprisebox.BoxStatus
import com.foodrescue.backend.surprisebox.InspectionResult
import com.foodrescue.backend.surprisebox.SaleBatchStatus
import com.foodrescue.backend.surprisebox.dto.BoxDetail
import com.foodrescue.backend.surprisebox.dto.BoxPackRequest
import com.foodrescue.backend.surprisebox.entity.BoxInspection
import com.foodrescue.backend.surprisebox.entity.BoxItem
import com.foodrescue.backend.surprisebox.entity.PhysicalBox
import com.foodrescue.backend.surprisebox.repository.BoxInspectionRepository
import com.foodrescue.backend.surprisebox.repository.BoxItemRepository
import com.foodrescue.backend.surprisebox.repository.PhysicalBoxRepository
import com.foodrescue.backend.surprisebox.repository.SaleBatchRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.util.UUID

data class PackResult(val box: BoxDetail, val created: Boolean)

/**
 * UC-26. One transaction under the batch row lock: same requestKey returns the existing box (E3); prices and statuses are read
 * from the current menu (BR-11, E1); value and safety are checked per box (BR-12, BR-18) before anything is written.
 */
@Service
class BoxPackingService(
    private val access: BoxAccess,
    private val batches: SaleBatchRepository,
    private val boxes: PhysicalBoxRepository,
    private val boxItems: BoxItemRepository,
    private val inspections: BoxInspectionRepository,
    private val menu: MenuItemRepository,
    private val accounts: AccountRepository,
    private val codes: BoxCodeGenerator,
    private val history: StoreHistoryRecorder,
    private val views: BoxViews,
    private val clock: Clock
) {
    @Transactional
    fun pack(packerId: UUID, storeId: UUID, batchId: UUID, r: BoxPackRequest): PackResult {
        access.requirePack(packerId, storeId)
        val batch = batches.findForUpdate(batchId)?.takeIf { it.store.id == storeId } ?: throw ApiException(404, "NOT_FOUND", "Resource not found")
        boxes.findByPackRequestKey(r.requestKey!!)?.let { existing ->
            if (existing.currentBatch.id != batchId) throw ApiException(409, "CONFLICT", "requestKey was already used for another batch")
            return PackResult(views.detail(existing), false)
        }
        val now = clock.instant()
        if (batch.status !in PACKABLE || !batch.pickupEndsAt.isAfter(now)) throw ApiException(409, "CONFLICT", "Batch is not open for packing")

        val errors = mutableListOf<FieldViolation>(); val priceChanges = mutableListOf<FieldViolation>()
        val lines = r.items!!.mapIndexedNotNull { i, line ->
            val item = menu.findById(line.menuItemId!!).orElse(null)?.takeIf { it.store.id == storeId }
            when {
                item == null -> { errors += FieldViolation("items[$i].menuItemId", "Menu item not found"); null }
                item.status != MenuItemStatus.ACTIVE -> { errors += FieldViolation("items[$i].menuItemId", "Menu item is discontinued"); null }
                r.items.indexOfFirst { it.menuItemId == line.menuItemId } != i -> { errors += FieldViolation("items[$i].menuItemId", "Duplicate item"); null }
                !line.expiresAt!!.isAfter(now) -> { errors += FieldViolation("items[$i].expiresAt", "Already expired"); null }
                else -> {
                    if (line.expectedUnitPrice != null && line.expectedUnitPrice.compareTo(item.listedPrice) != 0)
                        priceChanges += FieldViolation("items[$i].expectedUnitPrice", item.listedPrice.setScale(0).toPlainString())
                    Triple(item, line.quantity!!, line.expiresAt)
                }
            }
        }
        val notes = r.inspectionNotes?.trim().orEmpty()
        if (r.inspectionPassed == false && notes.isEmpty()) errors += FieldViolation("inspectionNotes", "Required when the inspection fails")
        if (errors.isNotEmpty()) throw ApiException(400, "VALIDATION_FAILED", "Invalid request", errors)
        if (priceChanges.isNotEmpty()) throw ApiException(409, "PRICE_CHANGED", "Menu prices changed; review the box again", priceChanges)

        val total = lines.fold(BigDecimal.ZERO) { sum, (item, qty, _) -> sum + item.listedPrice * BigDecimal(qty) }.setScale(0)
        val safeUntil = lines.minOf { it.third }
        if (total < batch.minimumValue)
            errors += FieldViolation("items", "Total listed value $total is below the batch minimum ${batch.minimumValue.setScale(0)}")
        if (safeUntil.isBefore(batch.pickupEndsAt))
            errors += FieldViolation("items[${lines.indexOfFirst { it.third == safeUntil }}].expiresAt", "Expires before the end of the pickup window")
        if (errors.isNotEmpty()) throw ApiException(400, "VALIDATION_FAILED", "Invalid request", errors)

        val packer = accounts.findById(packerId).orElse(null) ?: authenticationFailed()
        val passed = r.inspectionPassed!!
        val warnings = lines.map { it.first.safetyWarnings.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("; ")
        val box = boxes.saveAndFlush(PhysicalBox(batch.store, batch, codes.next(), packer, now, total, safeUntil, r.storageInstructions!!.trim(),
            warnings.ifEmpty { batch.safetyWarnings }, if (passed) BoxStatus.AVAILABLE else BoxStatus.BLOCKED, r.requestKey))
        boxItems.saveAll(lines.map { (item, qty, expires) -> BoxItem(batch.store, box, item, qty, item.name, item.listedPrice, item.safetyWarnings, expires) })
        inspections.save(BoxInspection(box, packer, now, if (passed) InspectionResult.PASSED else InspectionResult.FAILED, safeUntil,
            notes.ifEmpty { "Packed and checked" }))
        boxItems.flush()
        history.record(packer, batch.store, ENTITY, box.id!!, "BOX_PACKED", null, mapOf("boxCode" to box.boxCode, "batchId" to batch.id,
            "totalListedValue" to total, "inspection" to if (passed) "PASSED" else "FAILED"))
        return PackResult(views.detail(box), true)
    }

    companion object {
        const val ENTITY = "PHYSICAL_BOX"
        val PACKABLE = setOf(SaleBatchStatus.DRAFT, SaleBatchStatus.OPEN, SaleBatchStatus.PAUSED)
    }
}
