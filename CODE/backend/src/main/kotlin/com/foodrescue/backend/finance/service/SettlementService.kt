// AC-01..12 (T8.4): SettlementService responsibility.
package com.foodrescue.backend.finance.service
import com.foodrescue.backend.finance.*
import com.foodrescue.backend.finance.dto.*
import com.foodrescue.backend.finance.entity.*
import com.foodrescue.backend.finance.repository.*
import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.store.repository.StoreRepository
import com.foodrescue.backend.store.service.StoreHistoryRecorder
import com.foodrescue.backend.order.OrderStatus
import com.foodrescue.backend.order.repository.CustomerOrderRepository
import com.foodrescue.backend.pickup.CompensationStatus
import com.foodrescue.backend.pickup.repository.NoShowCaseRepository
import com.foodrescue.backend.complaint.*
import com.foodrescue.backend.notification.service.NotificationOutboxService
import jakarta.persistence.EntityManager
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.dao.DataAccessException
import java.sql.SQLException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.security.access.prepost.PreAuthorize
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.sql.Timestamp
import java.util.UUID
import java.security.MessageDigest

@Service
@PreAuthorize("hasRole('ADMIN')")
class SettlementService(private val stores: StoreRepository,private val settlements: SettlementRepository,private val lines: SettlementLineRepository,
    private val ledger: LedgerEntryRepository,private val events: FinancialEventRepository,private val orders: CustomerOrderRepository,
    private val cases: NoShowCaseRepository,private val actors: AccountRepository,private val orderLedger: OrderLedgerService,
    private val queries: SettlementQueryService,private val jdbc: NamedParameterJdbcTemplate,private val em: EntityManager,
    private val mapper: ObjectMapper,private val history: StoreHistoryRecorder,private val outbox: NotificationOutboxService,private val clock: Clock) {
    private data class Candidates(val eligible: List<LedgerEntry>,val held: List<HeldExceptionView>,val exceptions: List<HeldExceptionView>) {
        fun fingerprint(): String {
            val canonical=eligible.map { "L:${it.id}:${it.amount.setScale(0).toPlainString()}" }+
                held.map { "H:${it.orderId}:${it.ledgerEntryId}:${it.reason}:${it.amount.setScale(0).toPlainString()}" }+
                exceptions.map { "E:${it.orderId}:${it.reason}:${it.detail}" }
            return MessageDigest.getInstance("SHA-256").digest(canonical.sorted().joinToString("\n").toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        }
    }
    private fun candidates(store: UUID,end: Instant,draftId: UUID?): Candidates {
        em.flush()
        val frozenOrders=try {
            jdbc.queryForList("SELECT id FROM customer_order WHERE store_id=:store ORDER BY id FOR UPDATE NOWAIT",mapOf("store" to store),UUID::class.java).filterNotNull()
        } catch (e: DataAccessException) {
            // JDBC exception subtypes vary by translator; PostgreSQL SQLSTATE is stable.
            if(generateSequence<Throwable>(e) { it.cause }.filterIsInstance<SQLException>().any { it.sqlState=="55P03" })
                settlementConflict("Orders are being updated; retry settlement after the current operation completes")
            throw e
        }
        // Freeze existing orders before reading ledger/events so an in-flight refund cannot leave a stale candidate set.
        frozenOrders.forEach { orders.findForUpdate(it) ?: settlementMissing() }
        if(frozenOrders.isEmpty()) return Candidates(emptyList(),emptyList(),emptyList())
        val args=mapOf("store" to store,"end" to Timestamp.from(end),"draft" to draftId,"orders" to frozenOrders)
        val candidateIds=jdbc.queryForList("""SELECT l.id FROM ledger_entry l JOIN financial_event e ON e.id=l.event_id
            LEFT JOIN ledger_entry original ON original.id=l.original_entry_id
            WHERE l.store_id=:store AND e.order_id IN (:orders) AND l.occurred_at<:end AND
            (l.type IN ('STORE_PAYABLE','NO_SHOW_COMPENSATION') OR (l.type='ADJUSTMENT' AND original.type IN ('STORE_PAYABLE','NO_SHOW_COMPENSATION')))
            AND NOT EXISTS(SELECT 1 FROM settlement_line sl WHERE sl.ledger_entry_id=l.id AND (cast(:draft AS uuid) IS NULL OR sl.settlement_id<>:draft))
            ORDER BY l.id""",args,UUID::class.java).filterNotNull()
        val orderIds=jdbc.queryForList("""SELECT DISTINCT e.order_id FROM financial_event e JOIN customer_order o ON o.id=e.order_id
            WHERE o.store_id=:store AND e.order_id IN (:orders) AND e.occurred_at<:end AND e.type IN ('ORDER_COMPLETED','NO_SHOW_CONFIRMED','COMPLAINT_RESOLVED') ORDER BY e.order_id""",args,UUID::class.java).filterNotNull().toMutableSet()
        val eligible=mutableListOf<LedgerEntry>();val held=mutableListOf<HeldExceptionView>();val exceptions=mutableListOf<HeldExceptionView>()
        candidateIds.forEach { orderIds.add(ledger.findById(it).orElseThrow().event.order.id!!) }
        // Live complaint/refund/pickup workflows hold the order too: freeze classification through commit.
        orderIds.sorted().forEach { orderId ->
            val order=orders.findForUpdate(orderId) ?: settlementMissing()
            val pending=candidateIds.map { ledger.findById(it).orElseThrow() }.filter { it.event.order.id==orderId }
            val active=jdbc.queryForObject("SELECT count(*) FROM complaint WHERE order_id=:id AND status IN ('OPEN','INVESTIGATING','AWAITING_EVIDENCE','DECIDED')",mapOf("id" to orderId),Int::class.java)!!>0
            val n=cases.findByOrderId(orderId)
            val reason=when {
                active -> if(order.status==OrderStatus.NO_SHOW) "OPEN_DISPUTE" else "OPEN_COMPLAINT"
                order.status==OrderStatus.NO_SHOW && n?.compensationStatus in setOf(CompensationStatus.HELD,CompensationStatus.DISPUTED) -> "NO_SHOW_HELD"
                else -> null
            }
            val l=orderLedger.view(orderId)
            val orderEvents=events.findAllByOrderIdOrderByOccurredAtAscIdAsc(orderId).filter { it.occurredAt<end }
            fun missing(detail: String) { exceptions.add(HeldExceptionView(orderId,null,"MISSING_LEDGER_ENTRY",BigDecimal.ZERO,detail)) }
            if(orderEvents.any { it.type==FinancialEventType.ORDER_COMPLETED } &&
                (l.entries.none { it.type=="STORE_PAYABLE" } || l.entries.none { it.type=="PLATFORM_COMMISSION" })) missing("Completion allocation is missing")
            if(orderEvents.any { it.type==FinancialEventType.NO_SHOW_CONFIRMED } && (n==null || (n.retainedAmount.signum()>0 && l.entries.none { it.type=="NO_SHOW_COMPENSATION" }))) missing("No-show compensation is missing")
            if(reason!=null) {
                pending.forEach { held.add(HeldExceptionView(orderId,it.id,reason,it.amount.setScale(0),"Funds are held until dispute and refund resolution")) }
                return@forEach
            }
            orderEvents.filter { it.type==FinancialEventType.COMPLAINT_RESOLVED }.forEach { event ->
                val c=event.complaint
                if(c!=null && c.status==ComplaintStatus.CLOSED && c.outcome!=ComplaintOutcome.REJECTED) {
                    val expected=mutableListOf<String>()
                    val storeAdj=c.storeAdjustmentAmount ?: BigDecimal.ZERO;val commAdj=c.commissionAdjustmentAmount ?: BigDecimal.ZERO;val refund=c.approvedRefundAmount ?: BigDecimal.ZERO
                    if(n?.compensationStatus==CompensationStatus.FORFEITED && n.retainedAmount.signum()>0) {
                        expected.add("COMPLAINT_RESOLVED:NO_SHOW_FORFEIT:${c.id}")
                        if(storeAdj.signum()!=0) expected.add("COMPLAINT_RESOLVED:STORE_ADJ:${c.id}")
                    } else if(order.status==OrderStatus.COMPLETED) {
                        var sa=storeAdj;var ca=commAdj
                        if(sa.signum()==0 && ca.signum()==0 && refund.signum()>0) {
                            ca=if(c.responsibility==ComplaintResponsibility.PLATFORM) refund.negate() else MoneyRounding.applyRate(refund,order.commissionRate).negate()
                            sa=refund.negate()-ca
                        }
                        if(sa.signum()!=0) expected.add("COMPLAINT_RESOLVED:STORE_ADJ:${c.id}")
                        if(ca.signum()!=0) expected.add("COMPLAINT_RESOLVED:COMM_ADJ:${c.id}")
                    }
                    if(expected.any { !ledger.existsByBusinessKey(it) }) missing("Complaint ${c.id} adjustment is missing")
                }
            }
            if(!l.balanced && order.status==OrderStatus.COMPLETED)
                exceptions.add(HeldExceptionView(orderId,null,"LEDGER_IMBALANCE",l.netAllocated-l.netCollected,"Order allocations do not match verified net receipts"))
            if(order.status==OrderStatus.NO_SHOW && n?.compensationStatus !in setOf(CompensationStatus.ELIGIBLE,CompensationStatus.SETTLED,CompensationStatus.FORFEITED)) {
                if(pending.isNotEmpty()) missing("No-show compensation status is unavailable")
            } else if(order.status in setOf(OrderStatus.COMPLETED,OrderStatus.NO_SHOW) && exceptions.none { it.orderId==orderId }) eligible.addAll(pending)
        }
        return Candidates(eligible.sortedBy { it.id },held.sortedWith(compareBy({it.orderId},{it.ledgerEntryId})),exceptions.distinct().sortedWith(compareBy({it.orderId},{it.reason},{it.detail})))
    }
    @Transactional
    fun createOrRebuildDraft(adminId: UUID,r: CreateSettlementDraftRequest): SettlementDetailView {
        val store=stores.findForUpdate(r.storeId!!) ?: settlementMissing()
        val start=r.periodStartsAt!!;val end=r.periodEndsAt!!
        if(end<=start) settlementInvalid("periodEndsAt must be after periodStartsAt")
        val existing=settlements.findFirstByStoreIdAndStatus(store.id!!,SettlementStatus.DRAFT)
        val previous=settlements.findFirstByStoreIdAndStatusNotOrderByPeriodEndsAtDescCreatedAtDesc(store.id!!,SettlementStatus.DRAFT)
        if(previous!=null && (start<previous.periodEndsAt || end<=previous.periodEndsAt)) settlementConflict("Settlement period overlaps or precedes a confirmed period")
        val current=existing?.let { settlements.findForUpdate(it.id!!)!! }
        val candidate=candidates(store.id!!,end,current?.id)
        val id=if(current!=null) {
            em.flush();jdbc.update("DELETE FROM settlement_line WHERE settlement_id=:id",mapOf("id" to current.id));em.clear();current.id!!
        } else settlements.saveAndFlush(Settlement(store,start,end,SettlementStatus.DRAFT,BigDecimal.ZERO,previousSettlement=previous,settlementCode="STL-${UUID.randomUUID()}")).id!!
        val draft=settlements.findForUpdate(id)!!
        draft.periodStartsAt=start;draft.periodEndsAt=end;draft.previousSettlement=previous?.id?.let { settlements.getReferenceById(it) }
        draft.totalAmount=candidate.eligible.fold(BigDecimal.ZERO) { sum,e -> sum+e.amount }.setScale(0)
        draft.snapshotFingerprint=candidate.fingerprint();draft.snapshotTakenAt=clock.instant()
        draft.heldExceptionsSnapshot=mapper.writeValueAsString(SettlementSnapshotView(candidate.held,candidate.exceptions))
        settlements.saveAndFlush(draft)
        candidate.eligible.forEach { lines.save(SettlementLine(draft.store,draft,ledger.getReferenceById(it.id!!),it.amount.setScale(0))) };lines.flush()
        history.record(actors.getReferenceById(adminId),draft.store,"SETTLEMENT",id,"SETTLEMENT_DRAFT_SAVED",null,mapOf("totalAmount" to draft.totalAmount,"snapshotFingerprint" to draft.snapshotFingerprint))
        return queries.detail(draft)
    }
    @Transactional
    fun confirm(adminId: UUID,id: UUID): SettlementDetailView {
        val storeId=jdbc.queryForList("SELECT store_id FROM settlement WHERE id=:id",mapOf("id" to id),UUID::class.java).firstOrNull() ?: settlementMissing()
        stores.findForUpdate(storeId) ?: settlementMissing();val s=settlements.findForUpdate(id)!!
        if(s.status!=SettlementStatus.DRAFT) return queries.detail(s)
        val candidate=candidates(storeId,s.periodEndsAt,id)
        if(candidate.exceptions.isNotEmpty()) settlementConflict("Settlement has unresolved exceptions and cannot be confirmed")
        val stored=lines.findAllBySettlementIdOrderByCreatedAtAscIdAsc(id).associate { it.ledgerEntry.id to it.amount.setScale(0) }
        val expected=candidate.eligible.associate { it.id to it.amount.setScale(0) }
        if(candidate.fingerprint()!=s.snapshotFingerprint || stored!=expected || s.totalAmount.compareTo(expected.values.fold(BigDecimal.ZERO,BigDecimal::add))!=0)
            settlementConflict("Settlement underlying data changed since draft creation; rebuild draft before confirming")
        s.status=SettlementStatus.CONFIRMED;s.confirmedBy=actors.getReferenceById(adminId);s.confirmedAt=clock.instant();settlements.saveAndFlush(s)
        candidate.eligible.filter { it.type==LedgerEntryType.NO_SHOW_COMPENSATION }.forEach { e ->
            cases.findByOrderId(e.event.order.id!!)?.let { n ->
                val locked=cases.findForUpdate(n.id!!)!!
                if(locked.compensationStatus==CompensationStatus.ELIGIBLE) locked.compensationStatus=CompensationStatus.SETTLED
            }
        }
        history.record(s.confirmedBy,s.store,"SETTLEMENT",id,"SETTLEMENT_CONFIRMED",null,mapOf("totalAmount" to s.totalAmount,"status" to s.status))
        outbox.enqueueSettlement(s.store.owner,"SETTLEMENT_CONFIRMED","Settlement confirmed; payout has not been recorded",mapOf("settlementId" to id,"totalAmount" to s.totalAmount),dedupeKey="SETTLEMENT_CONFIRMED:$id")
        return queries.detail(s)
    }
}
