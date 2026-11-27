// AC-01..05 (T6.5): ManualRefundService responsibility.
package com.foodrescue.backend.payment.service
import com.foodrescue.backend.notification.service.NotificationOutboxService
import com.foodrescue.backend.notification.NotificationType as NType

import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.catalog.service.PriceValidator
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.exception.authenticationFailed
import com.foodrescue.backend.common.response.FieldViolation
import com.foodrescue.backend.common.response.PageResponse
import com.foodrescue.backend.common.validation.PageQuery
import com.foodrescue.backend.file.entity.Attachment
import com.foodrescue.backend.file.repository.AttachmentRepository
import com.foodrescue.backend.file.repository.StoredFileRepository
import com.foodrescue.backend.finance.FinancialEventType
import com.foodrescue.backend.finance.LedgerEntryType
import com.foodrescue.backend.finance.entity.FinancialEvent
import com.foodrescue.backend.finance.entity.LedgerEntry
import com.foodrescue.backend.finance.repository.FinancialEventRepository
import com.foodrescue.backend.finance.repository.LedgerEntryRepository
import com.foodrescue.backend.payment.PaymentStatus
import com.foodrescue.backend.payment.RefundRequestStatus
import com.foodrescue.backend.payment.RefundStatus
import com.foodrescue.backend.payment.dto.*
import com.foodrescue.backend.payment.entity.Refund
import com.foodrescue.backend.payment.entity.RefundRequest
import com.foodrescue.backend.payment.repository.PaymentRepository
import com.foodrescue.backend.payment.repository.RefundRepository
import com.foodrescue.backend.payment.repository.RefundRequestRepository
import com.foodrescue.backend.store.service.StoreHistoryRecorder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.util.UUID

/**
 * UC-72 with roadmap D3: the administrator decides the amount, transfers it outside the system, then records the result. Only a recorded success
 * changes money state, once, linked to the original payment; a failure leaves the obligation open (E3). Callers are ADMIN.
 */
@Service
class ManualRefundService(private val outbox: NotificationOutboxService,
    private val complaints: com.foodrescue.backend.complaint.repository.ComplaintRepository,
    private val orders: com.foodrescue.backend.order.repository.CustomerOrderRepository,
    private val obligations: RefundRequestRepository,
    private val refunds: RefundRepository,
    private val payments: PaymentRepository,
    private val files: StoredFileRepository,
    private val attachments: AttachmentRepository,
    private val accounts: AccountRepository,
    private val events: FinancialEventRepository,
    private val ledger: LedgerEntryRepository,
    private val prices: PriceValidator,
    private val history: StoreHistoryRecorder,
    private val views: RefundViews,
    private val clock: Clock
) {
    @Transactional(readOnly = true)
    fun list(status: RefundRequestStatus?, page: Int, size: Int): PageResponse<RefundRequestView> {
        val pageable = PageQuery().pageable(page, size, "createdAt")
        val result = if (status == null) obligations.findAll(pageable) else obligations.findAllByStatus(status, pageable)
        return PageResponse(result.content.map { views.view(it, true) }, page, size, result.totalElements, result.totalPages)
    }

    @Transactional(readOnly = true)
    fun detail(id: UUID): RefundRequestView = views.view(obligations.findById(id).orElse(null) ?: notFound(), true)

    @Transactional
    fun approve(adminId: UUID, id: UUID, r: RefundApproveRequest): RefundRequestView {
        // T8.1: serialize reservations and transfers across all obligations of the same order.
        orders.findForUpdate(obligations.findOrderId(id) ?: notFound()) ?: notFound()
        val o = obligations.findForUpdate(id) ?: notFound()
        if (o.status != RefundRequestStatus.PENDING && o.status != RefundRequestStatus.APPROVED) throw conflict("Refund obligation is ${o.status}")
        prices.check(r.amount!!, "amount")?.let { throw invalid(it) }
        val amount = prices.normalize(r.amount)
        if (amount > o.requestedAmount) throw invalid(FieldViolation("amount", "Must not exceed the requested ${o.requestedAmount.setScale(0)}"))
        val refundable = refundable(o)
        if (amount > refundable) throw invalid(FieldViolation("amount", "Only $refundable can still be refunded from the original payment"))   // UC-72 E1
        val admin = accounts.findById(adminId).orElse(null) ?: authenticationFailed()
        o.approvedAmount = amount; o.approvedBy = admin; o.approvedAt = clock.instant(); o.status = RefundRequestStatus.APPROVED
        obligations.saveAndFlush(o)
        history.record(admin, o.order.store, ENTITY, o.id!!, "REFUND_APPROVED", null, mapOf("amount" to amount, "note" to r.note!!.trim()))
        outbox.orderEvent(o.order,NType.REFUND,"REFUND_APPROVED",key="REFUND_APPROVED:${o.id}:${o.version}",payload=mapOf("refundRequestId" to o.id,"amount" to amount))
        return views.view(o, true)
    }

    /** Lock order then obligation: two confirmations of the same refund give one success and one 409 (BR-T65-02). */
    @Transactional
    fun confirm(adminId: UUID, id: UUID, r: RefundConfirmRequest): RefundRequestView {
        // T8.1: serialize reservations and transfers across all obligations of the same order.
        orders.findForUpdate(obligations.findOrderId(id) ?: notFound()) ?: notFound()
        val o = obligations.findForUpdate(id) ?: notFound()
        if (o.status != RefundRequestStatus.APPROVED) throw conflict("Refund obligation must be APPROVED (it is ${o.status})")
        val bankId = r.bankTransactionId!!.trim()
        if (refunds.existsByProviderRefundId(bankId)) throw conflict("This bank transaction id is already recorded")
        val file = files.findById(r.evidenceFileId!!).orElse(null)?.takeIf { it.uploadedBy.id == adminId }
            ?: throw invalid(FieldViolation("evidenceFileId", "File not found"))
        val payment = o.payment ?: throw conflict("Refund obligation has no original payment")
        val amount = o.approvedAmount!!
        if (amount > refundable(o)) throw conflict("Refund would exceed the original payment")
        val admin = accounts.findById(adminId).orElse(null) ?: authenticationFailed()
        val now = clock.instant()
        val refund = refunds.saveAndFlush(Refund(o.order, o, payment, amount, RefundStatus.SUCCEEDED, bankId, "refund:${o.id}:SUCCESS", now,
            recordedBy = admin, transferredAt = r.transferredAt, note = r.note?.trim()?.takeIf { it.isNotEmpty() }))
        attachments.save(Attachment(file, refund = refund))
        o.status = RefundRequestStatus.FULFILLED; obligations.saveAndFlush(o)
        o.complaint?.id?.let { complaintId ->
            val c = complaints.findForUpdate(complaintId)!!
            if (c.status == com.foodrescue.backend.complaint.ComplaintStatus.DECIDED &&
                obligations.findAllByComplaintIdOrderByCreatedAt(complaintId).all { it.status == RefundRequestStatus.FULFILLED }) {
                c.status = com.foodrescue.backend.complaint.ComplaintStatus.CLOSED; c.closedAt = now
                outbox.orderEvent(c.order,NType.COMPLAINT,"COMPLAINT_CLOSED",key="COMPLAINT_CLOSED:${c.id}",payload=mapOf("complaintId" to c.id))
                history.record(admin, c.store, "COMPLAINT", complaintId, "COMPLAINT_CLOSED", null, mapOf("status" to c.status))
            }
        }
        val event = events.saveAndFlush(FinancialEvent(o.order, FinancialEventType.REFUND_SUCCEEDED, refund = refund, occurredAt = now,
            businessKey = "REFUND_SUCCEEDED:${refund.id}"))
        // Only money that was booked as order revenue is reversed in the store ledger (BR-T65-05).
        if (payment.status == PaymentStatus.VERIFIED)
            ledger.save(LedgerEntry(event, o.order.store, LedgerEntryType.CUSTOMER_REFUND, amount.negate(), payment.currency, occurredAt = now))
        outbox.orderEvent(o.order,NType.REFUND,"REFUND_SUCCEEDED",key="REFUND_SUCCEEDED:${refund.id}",payload=mapOf("refundRequestId" to o.id,"refundId" to refund.id,"amount" to amount))
        history.record(admin, o.order.store, ENTITY, o.id!!, "REFUND_SUCCEEDED", null, mapOf("amount" to amount, "bankTransactionId" to bankId))
        return views.view(o, true)
    }

    @Transactional
    fun fail(adminId: UUID, id: UUID, note: String): RefundRequestView {
        val o = obligations.findForUpdate(id) ?: notFound()
        if (o.status != RefundRequestStatus.APPROVED) throw conflict("Refund obligation must be APPROVED (it is ${o.status})")
        val admin = accounts.findById(adminId).orElse(null) ?: authenticationFailed()
        val n = refunds.countByRequestId(o.id!!) + 1
        val failedRefund=refunds.saveAndFlush(Refund(o.order, o, o.payment!!, o.approvedAmount!!, RefundStatus.FAILED, null, "refund:${o.id}:FAILED-$n", null,
            recordedBy = admin, note = note.trim()))
        outbox.orderEvent(o.order,NType.REFUND,"REFUND_FAILED",key="REFUND_FAILED:${failedRefund.id}",payload=mapOf("refundRequestId" to o.id,"refundId" to failedRefund.id))
        history.record(admin, o.order.store, ENTITY, o.id!!, "REFUND_FAILED", null, mapOf("note" to note.trim()))
        return views.view(o, true)     // obligation stays APPROVED (UC-72 E3)
    }

    private fun refundable(o: RefundRequest): BigDecimal = o.payment?.let {
        val ownReservation = if (o.status in listOf(RefundRequestStatus.PENDING, RefundRequestStatus.APPROVED)) o.approvedAmount ?: o.requestedAmount else BigDecimal.ZERO
        it.receivedAmount - refunds.refundedForPayment(it.id!!) - obligations.openObligationsForPayment(it.id!!) + ownReservation
    } ?: BigDecimal.ZERO

    private fun invalid(v: FieldViolation) = ApiException(400, "VALIDATION_FAILED", "Invalid request", listOf(v))
    private fun conflict(m: String) = ApiException(409, "CONFLICT", m)
    private fun notFound(): Nothing = throw ApiException(404, "NOT_FOUND", "Resource not found")

    companion object { const val ENTITY = "REFUND_REQUEST" }
}
