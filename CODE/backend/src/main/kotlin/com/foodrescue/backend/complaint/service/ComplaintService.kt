// AC-01..11 (T8.1): ComplaintService responsibility.
package com.foodrescue.backend.complaint.service
import com.foodrescue.backend.complaint.*
import com.foodrescue.backend.complaint.dto.*
import com.foodrescue.backend.complaint.entity.Complaint
import com.foodrescue.backend.complaint.repository.ComplaintRepository
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.response.PageResponse
import com.foodrescue.backend.common.validation.PageQuery
import com.foodrescue.backend.file.entity.*
import com.foodrescue.backend.file.repository.AttachmentRepository
import com.foodrescue.backend.order.OrderStatus
import com.foodrescue.backend.order.repository.CustomerOrderRepository
import com.foodrescue.backend.pickup.repository.NoShowCaseRepository
import com.foodrescue.backend.pickup.CompensationStatus
import com.foodrescue.backend.store.service.StoreHistoryRecorder
import jakarta.persistence.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.data.jpa.domain.Specification
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.util.UUID

data class ComplaintSubmitResult(val view: CustomerComplaintDetail, val created: Boolean)
@Service
class ComplaintService(private val complaints: ComplaintRepository, private val orders: CustomerOrderRepository,
    private val cases: NoShowCaseRepository, private val attachments: AttachmentRepository, private val codes: ComplaintCodeGenerator,
    private val history: StoreHistoryRecorder, private val views: ComplaintViews, private val mapper: ObjectMapper,
    private val em: EntityManager, private val clock: Clock) {
    @Transactional
    fun submit(customerId: UUID, r: ComplaintSubmitRequest, key: String?): ComplaintSubmitResult {
        checkKey(key)
        val fp=mapper.writeValueAsString(listOf("COMPLAINT_SUBMIT",r.orderId,r.type,r.description!!.trim(),r.attachmentIds.distinct().sorted()))
        fun replay(): ComplaintSubmitResult? = key?.let { complaints.findByCustomerIdAndIdempotencyKeyAndSourceBoxIsNull(customerId,it) }?.let {
            if(it.requestFingerprint!=fp) conflict("IDEMPOTENCY_KEY_REUSED","Key was used with a different payload")
            ComplaintSubmitResult(views.customer(it),false)
        }
        replay()?.let { return it }
        // Serialize customer-scoped keys even when two submissions refer to different orders.
        if(key!=null) em.find(com.foodrescue.backend.account.entity.Account::class.java,customerId,LockModeType.PESSIMISTIC_WRITE)
        replay()?.let { return it }
        val o=orders.findForUpdate(r.orderId!!)?.takeIf { it.customer.id==customerId } ?: missing()
        if(complaints.existsByOrderIdAndTypeAndStatusIn(o.id!!,r.type!!,ACTIVE)) conflict("COMPLAINT_ALREADY_OPEN","An active complaint of this type already exists")
        var timely=true
        if(r.type==ComplaintType.NO_SHOW) {
            if(o.status!=OrderStatus.NO_SHOW) conflict("NO_SHOW_NOT_FOUND","Order has no confirmed no-show")
            val n=cases.findByOrderId(o.id!!)?.let { cases.findForUpdate(it.id!!) } ?: conflict("NO_SHOW_NOT_FOUND","No-show not found")
            timely=n.disputeDeadline?.let { !clock.instant().isAfter(it) } ?: true
            if(timely && n.compensationStatus==CompensationStatus.HELD) n.compensationStatus=CompensationStatus.DISPUTED
        } else if(r.type in listOf(ComplaintType.FOOD_SAFETY,ComplaintType.BOX_VALUE,ComplaintType.PICKUP) &&
            o.status !in listOf(OrderStatus.COMPLETED,OrderStatus.CANCELLED,OrderStatus.HANDOVER_IN_PROGRESS,OrderStatus.NO_SHOW)) {
            val delivered=em.createQuery("select count(a) from BoxAllocation a where a.order.id=:id and a.status=com.foodrescue.backend.order.AllocationStatus.DELIVERED",Long::class.javaObjectType).setParameter("id",o.id).singleResult.toLong()>0
            if(!delivered) conflict("INVALID_ORDER_STATUS","Order is not eligible for this complaint type")
        }
        val files=lockFiles(customerId,r.attachmentIds)
        val c=complaints.saveAndFlush(Complaint(o.store,o,o.customer,r.type,r.description.trim(),ComplaintStatus.OPEN,complaintCode=codes.next(),withinDisputeWindow=timely,idempotencyKey=key,requestFingerprint=fp))
        files.forEach { attachments.save(Attachment(it,complaint=c)) }; attachments.flush()
        history.record(o.customer,o.store,"COMPLAINT",c.id!!,"COMPLAINT_SUBMITTED",null,mapOf("status" to c.status))
        return ComplaintSubmitResult(views.customer(c),true)
    }
    @Transactional(readOnly=true)
    fun myList(customerId: UUID,status: ComplaintStatus?,orderId: UUID?,page: Int,size: Int): PageResponse<CustomerComplaintSummary> {
        val result=complaints.findAll(Specification { root,_,cb -> cb.and(*listOfNotNull(cb.equal(root.get<Any>("customer").get<UUID>("id"),customerId),
            status?.let { cb.equal(root.get<ComplaintStatus>("status"),it) },orderId?.let { cb.equal(root.get<Any>("order").get<UUID>("id"),it) }).toTypedArray()) },PageQuery().pageable(page,size,"createdAt"))
        return PageResponse(result.content.map(views::summary),page,size,result.totalElements,result.totalPages)
    }
    @Transactional(readOnly=true)
    fun myDetail(customerId: UUID,id: UUID)=views.customer(complaints.findById(id).orElse(null)?.takeIf { it.customer.id==customerId } ?: missing())
    @Transactional
    fun supplement(customerId: UUID,id: UUID,r: ComplaintSupplementRequest): CustomerComplaintDetail {
        val c=lock(id).takeIf { it.customer.id==customerId } ?: missing()
        if(c.status!=ComplaintStatus.AWAITING_EVIDENCE) conflict("INVALID_COMPLAINT_STATUS","Complaint is not awaiting evidence")
        lockFiles(customerId,r.attachmentIds).forEach { attachments.save(Attachment(it,complaint=c)) };attachments.flush()
        c.description+="\n[${clock.instant()}] ${r.description!!.trim()}";c.status=ComplaintStatus.INVESTIGATING
        history.record(c.customer,c.store,"COMPLAINT",id,"COMPLAINT_SUPPLEMENTED",null,mapOf("status" to c.status))
        return views.customer(c)
    }
    fun lock(id: UUID): Complaint {
        val orderId=complaints.findOrderId(id) ?: missing();orders.findForUpdate(orderId) ?: missing()
        return complaints.findForUpdate(id) ?: missing()
    }
    private fun lockFiles(actor: UUID,ids: List<UUID>)=ids.distinct().sorted().map { id ->
        val f=em.find(StoredFile::class.java,id,LockModeType.PESSIMISTIC_WRITE)?.takeIf { it.uploadedBy.id==actor } ?: invalid("Evidence file not found or not owned by you")
        if(attachments.findAllByFileId(id).isNotEmpty()) invalid("Evidence file is already attached")
        f
    }
    companion object {
        val ACTIVE=listOf(ComplaintStatus.OPEN,ComplaintStatus.INVESTIGATING,ComplaintStatus.AWAITING_EVIDENCE,ComplaintStatus.DECIDED)
        fun checkKey(key: String?) { if(key!=null && !key.matches(Regex("^[A-Za-z0-9_-]{1,255}$"))) invalid("Invalid Idempotency-Key") }
        fun missing(): Nothing=throw ApiException(404,"NOT_FOUND","Complaint or order not found")
        fun invalid(m: String): Nothing=throw ApiException(400,"VALIDATION_FAILED",m)
        fun conflict(code: String,m: String): Nothing=throw ApiException(409,code,m)
    }
}
