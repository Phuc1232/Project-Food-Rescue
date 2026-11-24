// AC-01..12 (T7.5): BoxUnfitProcessor responsibility.
package com.foodrescue.backend.surprisebox.service

import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.complaint.ComplaintStatus
import com.foodrescue.backend.complaint.ComplaintType
import com.foodrescue.backend.complaint.entity.Complaint
import com.foodrescue.backend.complaint.repository.ComplaintRepository
import com.foodrescue.backend.file.entity.Attachment
import com.foodrescue.backend.file.entity.StoredFile
import com.foodrescue.backend.file.repository.AttachmentRepository
import com.foodrescue.backend.order.AllocationStatus
import com.foodrescue.backend.order.OrderStatus
import com.foodrescue.backend.order.entity.CustomerOrder
import com.foodrescue.backend.order.repository.BoxAllocationRepository
import com.foodrescue.backend.order.service.OrderReleaser
import com.foodrescue.backend.order.service.PaidOrderCancellationExecutor
import com.foodrescue.backend.payment.repository.RefundRequestRepository
import com.foodrescue.backend.store.service.StoreHistoryRecorder
import com.foodrescue.backend.surprisebox.BoxStatus
import com.foodrescue.backend.surprisebox.InspectionResult
import com.foodrescue.backend.surprisebox.dto.*
import com.foodrescue.backend.surprisebox.entity.PhysicalBox
import com.foodrescue.backend.surprisebox.entity.BoxInspection
import com.foodrescue.backend.surprisebox.repository.*
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.util.UUID

/** Caller has locked all affected orders before the box. Also used while an entire batch is locked. */
@Component
class BoxUnfitProcessor(private val allocations: BoxAllocationRepository, private val inspections: BoxInspectionRepository,
    private val complaintCodes: com.foodrescue.backend.complaint.service.ComplaintCodeGenerator, private val complaints: ComplaintRepository, private val attachments: AttachmentRepository, private val refunds: RefundRequestRepository,
    private val releaser: OrderReleaser, private val cancellation: PaidOrderCancellationExecutor, private val views: BoxViews,
    private val history: StoreHistoryRecorder, private val mapper: ObjectMapper, private val em: EntityManager, private val clock: Clock) {
    fun fingerprint(operation: String, boxId: UUID, r: BoxUnfitRequest) = mapper.writeValueAsString(listOf(operation, boxId,
        r.reason!!.trim(), r.notes?.trim(), r.attachmentIds.distinct().sorted()))
    @Transactional(propagation = Propagation.MANDATORY)
    fun process(box: PhysicalBox, actor: Account, r: BoxUnfitRequest, related: List<CustomerOrder>, operation: String,
                key: String?, unsafeIds: Set<UUID> = setOf(box.id!!), batchMode: Boolean = false): BoxImpactResultView {
        val fingerprint = fingerprint(operation, box.id!!, r)
        if (key != null && box.unfitIdempotencyKey == key) {
            if (box.unfitFingerprint != fingerprint) conflict("IDEMPOTENCY_KEY_REUSED", "Key was used with a different operation or payload")
            return mapper.readValue(box.unfitResultJson!!, BoxImpactResultView::class.java)
        }
        val links = allocations.findAllByBoxIdOrderByReservedAtDesc(box.id!!)
        val deliveredOrder = related.firstOrNull { o -> o.status == OrderStatus.COMPLETED && links.any { it.order.id == o.id && it.status == AllocationStatus.DELIVERED } }
        val delivered = box.status == BoxStatus.DELIVERED || deliveredOrder != null
        val openComplaint = complaints.findFirstBySourceBoxIdAndStatusInOrderByCreatedAtDesc(box.id!!, OPEN_COMPLAINTS)
        if (delivered && openComplaint != null) {
            if (!batchMode) conflict("POST_DELIVERY_INCIDENT_ALREADY_OPEN", "A post-delivery food safety complaint is already open")
            return BoxImpactResultView(views.detail(box), "POST_DELIVERY_INCIDENT", emptyList(), emptyList(), openComplaint.id, emptyList())
        }
        val active = related.filter { o -> links.any { it.order.id == o.id && it.status in ACTIVE_ALLOCATIONS } && o.status in ACTIVE_ORDERS }
        if (!delivered && box.status == BoxStatus.BLOCKED && active.isEmpty() && !batchMode)
            conflict("BOX_ALREADY_BLOCKED", "Box is already blocked")
        val files = r.attachmentIds.distinct().sorted().map { id ->
            val file = em.find(StoredFile::class.java, id, LockModeType.PESSIMISTIC_WRITE)?.takeIf { it.uploadedBy.id == actor.id }
                ?: invalid("Evidence file not found or not owned by you")
            if (attachments.findAllByFileId(id).isNotEmpty()) invalid("Evidence file is already attached")
            file
        }
        val reason = r.reason!!.trim(); val notes = listOfNotNull(reason, r.notes?.trim()?.takeIf { it.isNotEmpty() }).joinToString("\n")
        inspections.save(BoxInspection(box, actor, clock.instant(), InspectionResult.FAILED, box.safeUntil, notes))
        var complaintId: UUID? = null
        val cancelled = mutableListOf<UUID>()
        if (delivered) {
            val order = deliveredOrder ?: related.firstOrNull { it.status == OrderStatus.COMPLETED }
                ?: conflict("INVALID_ORDER_STATUS", "Delivered box has no completed order")
            box.status = BoxStatus.DELIVERED
            val complaint = complaints.save(Complaint(box.store, order, order.customer, ComplaintType.FOOD_SAFETY, notes,
                ComplaintStatus.OPEN, complaintCode = complaintCodes.next(), sourceBox = box, idempotencyKey = key))
            complaintId = complaint.id
            files.forEach { attachments.save(Attachment(it, complaint = complaint)) }
        } else {
            box.status = BoxStatus.BLOCKED
            files.forEach { attachments.save(Attachment(it, box = box)) }
            active.sortedBy { it.id }.forEach { order ->
                cancelOrder(order, actor, "BOX_UNFIT: $reason", unsafeIds)
                cancelled.add(order.id!!)
            }
        }
        history.record(actor, box.store, "PHYSICAL_BOX", box.id!!,
            if (delivered) "BOX_POST_DELIVERY_INCIDENT_RECORDED" else if (operation == "STORE_BOX_UNFIT") "BOX_MARKED_UNFIT" else "ADMIN_BOX_BLOCKED",
            null, mapOf("reason" to reason, "cancelledOrderIds" to cancelled, "complaintId" to complaintId))
        inspections.flush()
        val refundIds = cancelled.flatMap { refunds.findAllByOrderIdOrderByCreatedAt(it).map { r -> r.id!! } }.distinct()
        val safe = cancelled.flatMap { allocations.findAllByOrderIdOrderByReservedAt(it) }
            .filter { it.status == AllocationStatus.RELEASED && it.box.status == BoxStatus.AVAILABLE }.map { it.box.id!! }.distinct()
        val result = BoxImpactResultView(views.detail(box), if (delivered) "POST_DELIVERY_INCIDENT" else "BOX_BLOCKED", cancelled, refundIds, complaintId, safe)
        if (key != null) { box.unfitIdempotencyKey = key; box.unfitFingerprint = fingerprint; box.unfitResultJson = mapper.writeValueAsString(result) }
        return result
    }
    fun cancelOrder(order: CustomerOrder, actor: Account, reason: String, unsafeIds: Set<UUID>) {
        if (order.status == OrderStatus.PENDING_PAYMENT) releaser.release(order, OrderStatus.CANCELLED, reason, actor, "ORDER_CANCELLED_BOX_UNFIT")
        else if (order.status in ACTIVE_ORDERS) {
            val orderUnsafe = allocations.findAllByOrderIdOrderByReservedAt(order.id!!)
                .filter { it.status in ACTIVE_ALLOCATIONS && it.box.id in unsafeIds }.map { it.box.id!! }
            cancellation.forceCancelStoreFault(order, actor, reason, unsafeIds = orderUnsafe)
        }
    }
    companion object {
        val ACTIVE_ALLOCATIONS = listOf(AllocationStatus.HELD, AllocationStatus.CONFIRMED)
        val ACTIVE_ORDERS = listOf(OrderStatus.PENDING_PAYMENT, OrderStatus.CONFIRMED, OrderStatus.HANDOVER_IN_PROGRESS, OrderStatus.PENDING_NO_SHOW)
        val OPEN_COMPLAINTS = listOf(ComplaintStatus.OPEN, ComplaintStatus.INVESTIGATING, ComplaintStatus.AWAITING_EVIDENCE)
    }
    private fun conflict(code: String, message: String): Nothing = throw ApiException(409, code, message)
    private fun invalid(message: String): Nothing = throw ApiException(400, "VALIDATION_FAILED", message)
}
