// AC-01..11 (T8.1): AdminComplaintService responsibility.
package com.foodrescue.backend.complaint.service
import com.foodrescue.backend.notification.service.NotificationOutboxService
import com.foodrescue.backend.notification.NotificationType as NType
import com.foodrescue.backend.complaint.*
import com.foodrescue.backend.complaint.dto.*
import com.foodrescue.backend.complaint.entity.Complaint
import com.foodrescue.backend.complaint.repository.ComplaintRepository
import com.foodrescue.backend.complaint.service.ComplaintService.Companion.missing
import com.foodrescue.backend.complaint.service.ComplaintService.Companion.invalid
import com.foodrescue.backend.complaint.service.ComplaintService.Companion.conflict
import com.foodrescue.backend.complaint.service.ComplaintService.Companion.checkKey
import com.foodrescue.backend.complaint.service.ComplaintService.Companion.ACTIVE
import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.common.response.PageResponse
import com.foodrescue.backend.common.validation.PageQuery
import com.foodrescue.backend.payment.*
import com.foodrescue.backend.payment.repository.*
import com.foodrescue.backend.payment.entity.RefundRequest
import com.foodrescue.backend.finance.FinancialEventType
import com.foodrescue.backend.finance.entity.FinancialEvent
import com.foodrescue.backend.finance.repository.FinancialEventRepository
import com.foodrescue.backend.pickup.repository.NoShowCaseRepository
import com.foodrescue.backend.pickup.CompensationStatus
import com.foodrescue.backend.store.service.StoreHistoryRecorder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.data.jpa.domain.Specification
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Instant
import java.math.BigDecimal
import java.util.UUID
@Service
@PreAuthorize("hasRole('ADMIN')")
class AdminComplaintService(private val outbox: NotificationOutboxService,
    private val ledgerPosting: com.foodrescue.backend.finance.service.LedgerPostingService, private val complaints: ComplaintRepository,private val workflow: ComplaintService,private val views: ComplaintViews,
    private val accounts: AccountRepository,private val cases: NoShowCaseRepository,private val payments: PaymentRepository,
    private val refunds: RefundRepository,private val obligations: RefundRequestRepository,private val events: FinancialEventRepository,
    private val history: StoreHistoryRecorder,private val mapper: ObjectMapper,private val clock: Clock) {
    @Transactional(readOnly=true)
    fun list(status: ComplaintStatus?,type: ComplaintType?,storeId: UUID?,orderId: UUID?,from: Instant?,to: Instant?,page: Int,size: Int): PageResponse<AdminComplaintSummary> {
        if(from!=null && to!=null && from>to) invalid("from must not be after to")
        val result=complaints.findAll(Specification { root,_,cb -> cb.and(*listOfNotNull(
            status?.let { cb.equal(root.get<ComplaintStatus>("status"),it) },type?.let { cb.equal(root.get<ComplaintType>("type"),it) },
            storeId?.let { cb.equal(root.get<Any>("store").get<UUID>("id"),it) },orderId?.let { cb.equal(root.get<Any>("order").get<UUID>("id"),it) },
            from?.let { cb.greaterThanOrEqualTo(root.get<Instant>("createdAt"),it) },to?.let { cb.lessThanOrEqualTo(root.get<Instant>("createdAt"),it) }).toTypedArray()) },PageQuery().pageable(page,size,"createdAt"))
        return PageResponse(result.content.map(views::adminSummary),page,size,result.totalElements,result.totalPages)
    }
    @Transactional(readOnly=true)
    fun detail(id: UUID)=views.admin(complaints.findById(id).orElse(null) ?: missing())
    @Transactional
    fun requestInfo(adminId: UUID,id: UUID,r: ComplaintRequestInfoRequest): AdminComplaintDetail {
        val c=workflow.lock(id)
        if(c.status !in listOf(ComplaintStatus.OPEN,ComplaintStatus.INVESTIGATING)) conflict("INVALID_COMPLAINT_STATUS","Cannot request information in this status")
        c.status=ComplaintStatus.AWAITING_EVIDENCE;c.requestedInfoNote=r.note!!.trim();c.handledBy=accounts.getReferenceById(adminId)
        record(c,"COMPLAINT_INFO_REQUESTED");return views.admin(c)
    }
    @Transactional
    fun decide(adminId: UUID,id: UUID,r: ComplaintDecisionRequest,key: String?): AdminComplaintDetail {
        checkKey(key)
        val normalized=r.refundAllocations.sortedBy { it.paymentId }.map { listOf(it.paymentId,it.amount!!.stripTrailingZeros().toPlainString()) }
        val storeAdj=(r.storeAdjustmentAmount ?: BigDecimal.ZERO); val commAdj=(r.commissionAdjustmentAmount ?: BigDecimal.ZERO)
        if(storeAdj.stripTrailingZeros().scale()>0 || commAdj.stripTrailingZeros().scale()>0) invalid("Adjustments must be whole VND")
        val fp=mapper.writeValueAsString(listOf(r.outcome,r.responsibility,r.reason!!.trim(),normalized,storeAdj.stripTrailingZeros().toPlainString(),commAdj.stripTrailingZeros().toPlainString()))
        val c=workflow.lock(id)
        if(key!=null && c.decisionKey==key) {
            if(fp!=c.decisionFingerprint) conflict("IDEMPOTENCY_KEY_REUSED","Key was used with a different payload")
            return views.admin(c)
        }
        if(c.status in listOf(ComplaintStatus.DECIDED,ComplaintStatus.CLOSED)) conflict("COMPLAINT_ALREADY_DECIDED","Complaint decision is final")
        val n=cases.findByOrderId(c.order.id!!)?.let { cases.findForUpdate(it.id!!) }
        val sources=payments.findAllByOrderIdOrderByReceivedAt(c.order.id!!).sortedBy { it.id }.map { payments.findForUpdate(it.id!!)!! }.associateBy { it.id!! }
        if(r.refundAllocations.map { it.paymentId }.distinct().size!=r.refundAllocations.size) invalid("Duplicate payment allocation")
        if(r.outcome==ComplaintOutcome.REJECTED && (r.refundAllocations.isNotEmpty() || storeAdj.signum()!=0 || commAdj.signum()!=0)) invalid("Rejected complaints cannot refund or adjust money")
        val total=r.refundAllocations.fold(BigDecimal.ZERO) { sum,a ->
            val p=sources[a.paymentId] ?: invalid("Payment does not belong to the order")
            if(p.status!=PaymentStatus.VERIFIED || a.amount==null || a.amount.signum()<=0 || a.amount.stripTrailingZeros().scale()>0) invalid("Allocation requires a verified payment and positive whole VND")
            val available=p.receivedAmount-refunds.refundedForPayment(p.id!!)-obligations.openObligationsForPayment(p.id!!)
            if(a.amount>available) conflict("REFUND_EXCEEDS_REFUNDABLE","Refund exceeds remaining original payment funds")
            sum+a.amount
        }.setScale(0)
        val now=clock.instant()
        if(n!=null) {
            if(r.outcome!=ComplaintOutcome.REJECTED && r.responsibility in listOf(ComplaintResponsibility.STORE,ComplaintResponsibility.SHARED)) n.compensationStatus=CompensationStatus.FORFEITED
            if(r.outcome==ComplaintOutcome.REJECTED && n.compensationStatus==CompensationStatus.DISPUTED && !complaints.existsByOrderIdAndIdNotAndStatusIn(c.order.id!!,id,ACTIVE))
                n.compensationStatus=if(n.notificationReceivedAt!=null && n.disputeDeadline!=null && !now.isBefore(n.disputeDeadline)) CompensationStatus.ELIGIBLE else CompensationStatus.HELD
        }
        c.handledBy=accounts.getReferenceById(adminId);c.decision=r.reason.trim();c.outcome=r.outcome;c.responsibility=r.responsibility
        c.approvedRefundAmount=total;c.storeAdjustmentAmount=storeAdj.setScale(0);c.commissionAdjustmentAmount=commAdj.setScale(0)
        c.resolvedAt=now;c.decisionKey=key;c.decisionFingerprint=fp
        c.status=if(total.signum()>0) ComplaintStatus.DECIDED else ComplaintStatus.CLOSED;c.closedAt=if(total.signum()==0) now else null
        r.refundAllocations.forEach { a -> obligations.save(RefundRequest(order=c.order,complaint=c,reason="COMPLAINT_${r.outcome!!.name}: ${r.reason.trim()}",
            requestedAmount=a.amount!!.setScale(0),status=RefundRequestStatus.PENDING,payment=sources[a.paymentId],reasonCode="COMPLAINT_${r.outcome.name}")) }
        val resolvedEvent=events.saveAndFlush(FinancialEvent(c.order,FinancialEventType.COMPLAINT_RESOLVED,complaint=c,occurredAt=now,businessKey="COMPLAINT_RESOLVED:$id"))
        ledgerPosting.postComplaintResolvedIfSettled(c,resolvedEvent,now)
        complaints.flush();record(c,"COMPLAINT_DECIDED");if(c.status==ComplaintStatus.CLOSED) record(c,"COMPLAINT_CLOSED")
        return views.admin(c)
    }
    @Transactional
    fun close(adminId: UUID,id: UUID): AdminComplaintDetail {
        val c=workflow.lock(id)
        if(c.status!=ComplaintStatus.DECIDED) conflict("INVALID_COMPLAINT_STATUS","Complaint must be DECIDED")
        if(obligations.findAllByComplaintIdOrderByCreatedAt(id).any { it.status!=RefundRequestStatus.FULFILLED }) conflict("REFUND_PENDING","Refund obligations remain unfinished")
        c.status=ComplaintStatus.CLOSED;c.closedAt=clock.instant();c.handledBy=accounts.getReferenceById(adminId);record(c,"COMPLAINT_CLOSED")
        events.findByBusinessKey("COMPLAINT_RESOLVED:$id")?.let { ledgerPosting.postComplaintResolvedIfSettled(c,it,clock.instant()) }
        return views.admin(c)
    }
    private fun record(c: Complaint,action: String) {
        history.record(c.handledBy,c.store,"COMPLAINT",c.id!!,action,null,mapOf("status" to c.status,"outcome" to c.outcome,"approvedRefundAmount" to c.approvedRefundAmount))
        complaints.flush()
        val key=if(action=="COMPLAINT_INFO_REQUESTED") "$action:${c.id}:${c.version}" else "$action:${c.id}"
        outbox.orderEvent(c.order,NType.COMPLAINT,action,key=key,store=action=="COMPLAINT_DECIDED",
            payload=mapOf("complaintId" to c.id,"complaintCode" to c.complaintCode,"outcome" to c.outcome,"approvedRefundAmount" to c.approvedRefundAmount))
        complaints.flush() // The detail view reads audit history through JDBC in this transaction.
    }
}
