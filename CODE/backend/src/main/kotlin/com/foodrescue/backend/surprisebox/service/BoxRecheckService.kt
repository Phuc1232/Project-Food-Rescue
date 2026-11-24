// AC-01..14 (T7.4): BoxRecheckService responsibility.
package com.foodrescue.backend.surprisebox.service

import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.security.StoreAuthorizationService
import com.foodrescue.backend.order.AllocationStatus
import com.foodrescue.backend.order.OrderStatus
import com.foodrescue.backend.order.repository.BoxAllocationRepository
import com.foodrescue.backend.pickup.repository.NoShowCaseRepository
import com.foodrescue.backend.store.service.StoreHistoryRecorder
import com.foodrescue.backend.surprisebox.*
import com.foodrescue.backend.surprisebox.dto.*
import com.foodrescue.backend.surprisebox.entity.BoxInspection
import com.foodrescue.backend.surprisebox.repository.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

@Service
class BoxRecheckService(private val authorization: StoreAuthorizationService, private val boxes: PhysicalBoxRepository,
    private val allocations: BoxAllocationRepository, private val cases: NoShowCaseRepository, private val batches: SaleBatchRepository,
    private val inspections: BoxInspectionRepository, private val accounts: AccountRepository, private val views: BoxViews,
    private val history: StoreHistoryRecorder, private val clock: Clock) {
    @Transactional(readOnly = true)
    fun listAwaitingInspection(actorId: UUID, storeId: UUID): List<AwaitingInspectionBoxView> {
        authorization.requirePermission(actorId, storeId, "BOX_MANAGE")
        return boxes.findAllByStoreIdAndStatusOrderByPackedAt(storeId, BoxStatus.AWAITING_INSPECTION).mapNotNull { box ->
            val source = allocations.findAllByBoxIdOrderByReservedAtDesc(box.id!!).firstOrNull { it.status == AllocationStatus.RELEASED && it.order.status == OrderStatus.NO_SHOW } ?: return@mapNotNull null
            val case = cases.findByOrderId(source.order.id!!) ?: return@mapNotNull null
            AwaitingInspectionBoxView(views.box(box), views.detail(box).inspections.firstOrNull(), source.order.id!!,
                source.order.orderCode, case.id!!, case.verifiedAt)
        }
    }
    @Transactional
    fun recheck(actorId: UUID, storeId: UUID, boxId: UUID, r: BoxRecheckRequest): BoxDetail {
        authorization.requirePermission(actorId, storeId, "BOX_MANAGE")
        // Match packing/placement lock order when a target batch is supplied.
        val target = r.targetBatchId?.takeIf { r.result == InspectionResult.PASSED }?.let { batches.findForUpdate(it)?.takeIf { b -> b.store.id == storeId } ?: missing() }
        val box = boxes.findForUpdate(boxId)?.takeIf { it.store.id == storeId } ?: missing()
        if (box.status != BoxStatus.AWAITING_INSPECTION) conflict("BOX_NOT_AWAITING_INSPECTION", "Box is not awaiting inspection")
        if (allocations.existsByBoxIdAndStatusIn(boxId, listOf(AllocationStatus.HELD, AllocationStatus.CONFIRMED))) conflict("BOX_ALLOCATED", "Box has an active allocation")
        if (allocations.findAllByBoxIdOrderByReservedAtDesc(boxId).none { it.status == AllocationStatus.RELEASED && it.order.status == OrderStatus.NO_SHOW })
            conflict("BOX_NOT_FROM_NO_SHOW", "Box has no released no-show allocation")
        val now = clock.instant(); val safeUntil = r.safeUntil!!; val notes = r.notes?.trim().orEmpty()
        when (r.result!!) {
            InspectionResult.FAILED -> {
                if (notes.isEmpty()) invalid("Notes are required when inspection fails")
                box.status = BoxStatus.BLOCKED
                if (safeUntil.isAfter(box.packedAt)) box.safeUntil = safeUntil
            }
            InspectionResult.PASSED -> {
                if (!safeUntil.isAfter(now) || !safeUntil.isAfter(box.packedAt)) invalid("safeUntil must be after now and packing time")
                if (target != null) {
                    if (target.status !in listOf(SaleBatchStatus.DRAFT, SaleBatchStatus.OPEN, SaleBatchStatus.PAUSED) ||
                        !target.pickupEndsAt.isAfter(now) || safeUntil.isBefore(target.pickupEndsAt) || box.totalListedValue < target.minimumValue)
                        conflict("TARGET_BATCH_INELIGIBLE", "Target batch does not meet time, safety or minimum value requirements")
                    box.currentBatch = target; box.status = BoxStatus.AVAILABLE
                }
                box.safeUntil = safeUntil
            }
        }
        val actor = accounts.getReferenceById(actorId)
        inspections.saveAndFlush(BoxInspection(box, actor, now, r.result, safeUntil, notes.ifEmpty { "Rechecked and passed" }))
        history.record(actor, box.store, "PHYSICAL_BOX", boxId, "BOX_RECHECKED", null,
            mapOf("result" to r.result.name, "status" to box.status.name, "targetBatchId" to target?.id))
        return views.detail(box)
    }
    private fun missing(): Nothing = throw ApiException(404, "NOT_FOUND", "Box or target batch not found")
    private fun conflict(code: String, message: String): Nothing = throw ApiException(409, code, message)
    private fun invalid(message: String): Nothing = throw ApiException(400, "VALIDATION_FAILED", message)
}
