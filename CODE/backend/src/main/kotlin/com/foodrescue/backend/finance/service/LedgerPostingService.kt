// AC-01..10 (T8.3): LedgerPostingService responsibility.
package com.foodrescue.backend.finance.service
import com.foodrescue.backend.finance.*
import com.foodrescue.backend.finance.entity.*
import com.foodrescue.backend.finance.repository.*
import com.foodrescue.backend.finance.dto.LedgerSyncReportView
import com.foodrescue.backend.order.entity.CustomerOrder
import com.foodrescue.backend.order.OrderStatus
import com.foodrescue.backend.order.repository.CustomerOrderRepository
import com.foodrescue.backend.complaint.*
import com.foodrescue.backend.complaint.entity.Complaint
import com.foodrescue.backend.pickup.CompensationStatus
import com.foodrescue.backend.pickup.entity.NoShowCase
import com.foodrescue.backend.pickup.repository.NoShowCaseRepository
import jakarta.persistence.EntityManager
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.*
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Service
class LedgerPostingService(private val ledger: LedgerEntryRepository, private val events: FinancialEventRepository,
    private val orders: CustomerOrderRepository, private val cases: NoShowCaseRepository,
    private val jdbc: NamedParameterJdbcTemplate, private val em: EntityManager, private val properties: FinanceLedgerProperties) {
    private fun insert(event: FinancialEvent, type: LedgerEntryType, amount: BigDecimal, key: String,
                       original: LedgerEntry? = null, at: Instant = event.occurredAt): Int {
        em.flush()
        return jdbc.update("""INSERT INTO ledger_entry(id,created_at,event_id,store_id,type,amount,currency,original_entry_id,occurred_at,business_key)
            VALUES (:id,:at,:event,:store,:type,:amount,:currency,:original,:at,:key)
            ON CONFLICT (business_key) WHERE business_key IS NOT NULL DO NOTHING""",
            mapOf("id" to UUID.randomUUID(), "at" to Timestamp.from(at), "event" to event.id, "store" to event.order.store.id,
                "type" to type.name, "amount" to amount.setScale(0), "currency" to event.order.currency, "original" to original?.id, "key" to key))
    }
    @Transactional(propagation = Propagation.MANDATORY)
    fun postOrderCompleted(order: CustomerOrder, completedEvent: FinancialEvent): Int {
        if (order.status != OrderStatus.COMPLETED) return 0
        val commission=MoneyRounding.applyRate(order.totalAmount,order.commissionRate)
        return insert(completedEvent,LedgerEntryType.PLATFORM_COMMISSION,commission,"ORDER_COMPLETED:COMMISSION:${order.id}") +
            insert(completedEvent,LedgerEntryType.STORE_PAYABLE,order.totalAmount-commission,"ORDER_COMPLETED:STORE_PAYABLE:${order.id}")
    }
    @Transactional(propagation = Propagation.MANDATORY)
    fun postNoShowConfirmed(order: CustomerOrder, noShowCase: NoShowCase, noShowEvent: FinancialEvent): Int =
        if(noShowCase.retainedAmount.signum()>0) insert(noShowEvent,LedgerEntryType.NO_SHOW_COMPENSATION,noShowCase.retainedAmount,"NO_SHOW_CONFIRMED:COMPENSATION:${noShowCase.id}") else 0

    @Transactional(propagation = Propagation.MANDATORY)
    fun postComplaintResolvedIfSettled(complaint: Complaint, resolvedEvent: FinancialEvent, occurredAt: Instant): Int {
        if(complaint.status!=ComplaintStatus.CLOSED || complaint.outcome==ComplaintOutcome.REJECTED) return 0
        val order=orders.findForUpdate(complaint.order.id!!) ?: return 0
        val n=cases.findByOrderId(order.id!!)
        var created=0
        val storeAdj=complaint.storeAdjustmentAmount ?: BigDecimal.ZERO
        var commAdj=complaint.commissionAdjustmentAmount ?: BigDecimal.ZERO
        if(n?.compensationStatus==CompensationStatus.FORFEITED) {
            events.findFirstByOrderIdAndType(order.id!!,FinancialEventType.NO_SHOW_CONFIRMED)?.let { created+=postNoShowConfirmed(order,n,it) }
            ledger.findByBusinessKey("NO_SHOW_CONFIRMED:COMPENSATION:${n.id}")?.let {
                created+=insert(resolvedEvent,LedgerEntryType.ADJUSTMENT,it.amount.negate(),"COMPLAINT_RESOLVED:NO_SHOW_FORFEIT:${complaint.id}",it,occurredAt)
                if(storeAdj.signum()!=0) created+=insert(resolvedEvent,LedgerEntryType.ADJUSTMENT,storeAdj,"COMPLAINT_RESOLVED:STORE_ADJ:${complaint.id}",it,occurredAt)
            }
        } else if(order.status==OrderStatus.COMPLETED) {
            events.findFirstByOrderIdAndType(order.id!!,FinancialEventType.ORDER_COMPLETED)?.let { created+=postOrderCompleted(order,it) }
            var payableAdj=storeAdj
            val refund=complaint.approvedRefundAmount ?: BigDecimal.ZERO
            if(storeAdj.signum()==0 && commAdj.signum()==0 && refund.signum()>0) {
                commAdj=if(complaint.responsibility==ComplaintResponsibility.PLATFORM) refund.negate() else MoneyRounding.applyRate(refund,order.commissionRate).negate()
                payableAdj=refund.negate()-commAdj
            }
            for((suffix,amount,originalKey) in listOf(Triple("STORE_ADJ",payableAdj,"STORE_PAYABLE"),Triple("COMM_ADJ",commAdj,"COMMISSION"))) {
                if(amount.signum()!=0) ledger.findByBusinessKey("ORDER_COMPLETED:$originalKey:${order.id}")?.let {
                    created+=insert(resolvedEvent,LedgerEntryType.ADJUSTMENT,amount,"COMPLAINT_RESOLVED:$suffix:${complaint.id}",it,occurredAt)
                }
            }
        }
        return created
    }
    @Transactional
    fun postPendingEvents(batchSize: Int? = null): LedgerSyncReportView {
        // Resolve configured defaults inside the transactional target, not Kotlin's static default bridge on a Spring proxy.
        val limit = batchSize ?: properties.ledgerBatchSize
        require(limit in 1..1000)
        // Select only events missing their expected entries. Settled zero/rejected decisions need no posting.
        // Lock orders first; try event locks afterwards to avoid event -> order deadlocks with live workflows.
        val candidates=jdbc.queryForList("""SELECT e.id FROM financial_event e JOIN customer_order o ON o.id=e.order_id
            LEFT JOIN complaint c ON c.id=e.complaint_id LEFT JOIN no_show_case n ON n.id=e.no_show_case_id
            WHERE (e.type='ORDER_COMPLETED' AND o.status='COMPLETED' AND
                (NOT EXISTS(SELECT 1 FROM ledger_entry l WHERE l.business_key='ORDER_COMPLETED:COMMISSION:'||o.id)
                 OR NOT EXISTS(SELECT 1 FROM ledger_entry l WHERE l.business_key='ORDER_COMPLETED:STORE_PAYABLE:'||o.id)))
            OR (e.type='NO_SHOW_CONFIRMED' AND n.retained_amount>0 AND NOT EXISTS(SELECT 1 FROM ledger_entry l WHERE l.business_key='NO_SHOW_CONFIRMED:COMPENSATION:'||n.id))
            OR (e.type='COMPLAINT_RESOLVED' AND c.status='CLOSED' AND c.outcome<>'REJECTED' AND
                ((EXISTS(SELECT 1 FROM no_show_case ns WHERE ns.order_id=o.id AND ns.compensation_status='FORFEITED' AND ns.retained_amount>0)
                    AND (NOT EXISTS(SELECT 1 FROM ledger_entry l WHERE l.business_key='COMPLAINT_RESOLVED:NO_SHOW_FORFEIT:'||c.id)
                        OR (coalesce(c.store_adjustment_amount,0)<>0 AND NOT EXISTS(SELECT 1 FROM ledger_entry l WHERE l.business_key='COMPLAINT_RESOLVED:STORE_ADJ:'||c.id))))
                OR (o.status='COMPLETED' AND (
                    ((coalesce(c.store_adjustment_amount,0)<>0 OR
                        (coalesce(c.store_adjustment_amount,0)=0 AND coalesce(c.commission_adjustment_amount,0)=0 AND c.approved_refund_amount>0 AND c.responsibility<>'PLATFORM'
                        AND c.approved_refund_amount-round(c.approved_refund_amount*o.commission_rate)<>0))
                      AND NOT EXISTS(SELECT 1 FROM ledger_entry l WHERE l.business_key='COMPLAINT_RESOLVED:STORE_ADJ:'||c.id))
                    OR ((coalesce(c.commission_adjustment_amount,0)<>0 OR
                        (coalesce(c.store_adjustment_amount,0)=0 AND coalesce(c.commission_adjustment_amount,0)=0 AND c.approved_refund_amount>0
                        AND (c.responsibility='PLATFORM' OR round(c.approved_refund_amount*o.commission_rate)<>0)))
                      AND NOT EXISTS(SELECT 1 FROM ledger_entry l WHERE l.business_key='COMPLAINT_RESOLVED:COMM_ADJ:'||c.id))))))
            ORDER BY e.occurred_at,e.id LIMIT :n FOR UPDATE OF o SKIP LOCKED""",mapOf("n" to limit),UUID::class.java).filterNotNull()
        var created=0; var scanned=0; var skipped=0
        candidates.forEach { id ->
            val locked=jdbc.queryForList("SELECT id FROM financial_event WHERE id=:id FOR UPDATE SKIP LOCKED",mapOf("id" to id),UUID::class.java)
            if(locked.isNotEmpty()) {
                val e=events.findById(id).orElseThrow(); val o=orders.findForUpdate(e.order.id!!)!!; scanned++
                skipped+=jdbc.queryForObject("SELECT count(*) FROM ledger_entry WHERE event_id=:id AND business_key IS NOT NULL",mapOf("id" to id),Int::class.java)!!
                created+=when(e.type) {
                    FinancialEventType.ORDER_COMPLETED -> postOrderCompleted(o,e)
                    FinancialEventType.NO_SHOW_CONFIRMED -> e.noShowCase?.let { postNoShowConfirmed(o,it,e) } ?: 0
                    FinancialEventType.COMPLAINT_RESOLVED -> e.complaint?.let { postComplaintResolvedIfSettled(it,e,it.closedAt ?: e.occurredAt) } ?: 0
                    else -> 0
                }
            }
        }
        return LedgerSyncReportView(scanned,created,skipped)
    }
}
