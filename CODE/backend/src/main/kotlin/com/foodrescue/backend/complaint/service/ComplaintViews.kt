// AC-01..11 (T8.1): ComplaintViews responsibility.
package com.foodrescue.backend.complaint.service
import com.foodrescue.backend.complaint.dto.*
import com.foodrescue.backend.complaint.entity.Complaint
import com.foodrescue.backend.order.service.*
import com.foodrescue.backend.order.repository.BoxAllocationRepository
import com.foodrescue.backend.order.dto.AllocatedBoxView
import com.foodrescue.backend.payment.repository.*
import com.foodrescue.backend.payment.service.RefundViews
import com.foodrescue.backend.pickup.repository.*
import com.foodrescue.backend.pickup.dto.*
import com.foodrescue.backend.pickup.CompensationStatus
import com.foodrescue.backend.surprisebox.service.BoxViews
import org.springframework.stereotype.Component
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID
import java.math.BigDecimal
@Component
class ComplaintViews(private val jdbc: JdbcTemplate, private val cases: NoShowCaseRepository, private val refunds: RefundRequestRepository,
    private val refundViews: RefundViews, private val money: OrderMoneyReader, private val orders: OrderViews, private val allocations: BoxAllocationRepository,
    private val boxes: BoxViews, private val payments: PaymentRepository, private val paidRefunds: RefundRepository, private val incidents: PickupIncidentRepository) {
    fun summary(c: Complaint) = CustomerComplaintSummary(c.id!!,c.complaintCode,c.order.id!!,c.order.orderCode,c.type,c.status,c.withinDisputeWindow,
        c.approvedRefundAmount,c.outcome,c.responsibility,c.createdAt,c.resolvedAt,c.closedAt)
    fun held(c: Complaint): BigDecimal = cases.findByOrderId(c.order.id!!)?.takeIf { it.compensationStatus in listOf(CompensationStatus.HELD,CompensationStatus.DISPUTED) }?.retainedAmount ?: BigDecimal.ZERO
    fun noShow(c: Complaint) = cases.findByOrderId(c.order.id!!)?.let { n -> NoShowCaseSummaryView(n.id!!,n.order.id!!,n.verifiedBy.id!!,n.verifiedAt,n.evidence,n.compensationStatus.name,n.notificationReceivedAt,n.disputeDeadline) }
    fun customer(c: Complaint) = CustomerComplaintDetail(c.id!!,c.complaintCode,c.order.id!!,c.order.orderCode,c.type,c.status,c.withinDisputeWindow,
        c.approvedRefundAmount,c.outcome,c.responsibility,c.createdAt,c.resolvedAt,c.closedAt,c.description,c.requestedInfoNote,c.decision,c.sourceBox?.id,
        jdbc.queryForList("select file_id from attachment where complaint_id=? order by id",UUID::class.java,c.id).filterNotNull(),
        refunds.findAllByComplaintIdOrderByCreatedAt(c.id!!).map { refundViews.view(it,false) },noShow(c),held(c),c.storeAdjustmentAmount,c.commissionAdjustmentAmount)
    fun adminSummary(c: Complaint) = AdminComplaintSummary(c.id!!,c.complaintCode,c.order.id!!,c.order.orderCode,c.type,c.status,c.withinDisputeWindow,
        c.approvedRefundAmount,c.outcome,c.responsibility,c.createdAt,c.resolvedAt,c.closedAt,c.store.id!!,c.store.name,c.customer.displayName,c.customer.loginIdentifier,money.money(c.order).verifiedPaid,held(c))
    fun admin(c: Complaint): AdminComplaintDetail {
        val id=c.order.id!!
        val sessions=jdbc.query("select id, actor_id, status, started_at, completed_at, idempotency_key from pickup_session where order_id=? order by started_at,id",
            { rs,_ -> PickupSessionView(rs.getObject("id",UUID::class.java),id,c.order.orderCode,rs.getObject("actor_id",UUID::class.java),rs.getString("status"),rs.getTimestamp("started_at").toInstant(),rs.getTimestamp("completed_at")?.toInstant(),rs.getString("idempotency_key")) },id)
        return AdminComplaintDetail(customer(c),orders.view(c.order),money.money(c.order),allocations.findAllByOrderIdOrderByReservedAt(id).map { a ->
            ComplaintBoxView(AllocatedBoxView(a.box.id!!,a.box.boxCode,a.box.totalListedValue,a.box.status.name,a.status.name,a.reservedAt,a.releasedAt,a.releaseReason),boxes.detail(a.box)) },
            payments.findAllByOrderIdOrderByReceivedAt(id).map { p -> val refunded=paidRefunds.refundedForPayment(p.id!!); val open=refunds.openObligationsForPayment(p.id!!)
                ComplaintPaymentView(p.id!!,p.status.name,p.receivedAmount,p.receivedAt,p.provider,p.providerTransactionId,refunded,open,p.receivedAmount-refunded-open) },
            refunds.findAllByOrderIdOrderByCreatedAt(id).map { refundViews.view(it,true) },ComplaintHandoverView(sessions,incidents.findAllByOrderIdOrderByOccurredAt(id).map {
                PickupIncidentView(it.id!!,id,it.session?.id,it.actor.id!!,it.reason,it.customerArrived,it.occurredAt,it.resolution) }),noShow(c),
            jdbc.queryForList("select id, entity_type, entity_id, action, created_at, actor_id, before_state, after_state from audit_log where (entity_type='COMPLAINT' and entity_id=?) or (entity_type='CUSTOMER_ORDER' and entity_id=?) order by created_at,id",c.id,id))
    }
}
