// AC-01..10 (T8.3): StoreFinanceService responsibility.
package com.foodrescue.backend.finance.service
import com.foodrescue.backend.finance.dto.*
import com.foodrescue.backend.finance.entity.LedgerEntry
import com.foodrescue.backend.finance.LedgerEntryType
import com.foodrescue.backend.finance.repository.LedgerEntryRepository
import com.foodrescue.backend.order.repository.CustomerOrderRepository
import com.foodrescue.backend.pickup.repository.NoShowCaseRepository
import com.foodrescue.backend.common.security.StoreAuthorizationService
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.response.PageResponse
import com.foodrescue.backend.common.validation.PageQuery
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
@Service
@Transactional(readOnly=true)
class StoreFinanceService(private val authorization: StoreAuthorizationService, private val orders: CustomerOrderRepository,
    private val ledger: LedgerEntryRepository, private val orderLedger: OrderLedgerService, private val cases: NoShowCaseRepository,
    private val jdbc: NamedParameterJdbcTemplate) {
    private fun authorize(actor: UUID,store: UUID) = authorization.requirePermission(actor,store,"STORE_PROFILE_MANAGE")
    private fun validate(from: Instant?,to: Instant?,page: Int,size: Int,sort: String,allowed: Set<String>) {
        if(from!=null && to!=null && from>to) throw ApiException(400,"VALIDATION_FAILED","from must not be after to")
        PageQuery().pageable(page,size,"createdAt")
        if(sort !in allowed) throw ApiException(400,"INVALID_PAGINATION","Invalid sort")
    }
    private fun <T> page(items: List<T>,page: Int,size: Int): PageResponse<T> {
        val start=(page.toLong()*size).coerceAtMost(items.size.toLong()).toInt()
        return PageResponse(items.subList(start,(start+size).coerceAtMost(items.size)),page,size,items.size.toLong(),(items.size+size-1)/size)
    }
    fun order(actor: UUID,store: UUID,id: UUID): OrderLedgerView {
        authorize(actor,store)
        val o=orders.findById(id).orElse(null)?.takeIf { it.store.id==store } ?: throw ApiException(404,"NOT_FOUND","Order not found")
        return orderLedger.view(o.id!!)
    }
    fun revenue(actor: UUID,store: UUID,from: Instant?,to: Instant?,code: String?,page: Int,size: Int,sort: String): StoreRevenueReportView {
        authorize(actor,store);validate(from,to,page,size,sort,setOf("completedAt","occurredAt"))
        val sortColumn=if(sort=="occurredAt") "coalesce((SELECT max(l.occurred_at) FROM ledger_entry l JOIN financial_event e ON e.id=l.event_id WHERE e.order_id=customer_order.id AND l.type='STORE_PAYABLE'),completed_at)" else "completed_at"
        val ids=jdbc.queryForList("""SELECT id FROM customer_order WHERE store_id=:store AND status='COMPLETED'
            AND (cast(:from AS timestamptz) IS NULL OR completed_at>=:from) AND (cast(:to AS timestamptz) IS NULL OR completed_at<=:to)
            AND (cast(:code AS text) IS NULL OR order_code=:code) ORDER BY $sortColumn DESC,id""",
            mapOf("store" to store,"from" to from?.let(java.sql.Timestamp::from),"to" to to?.let(java.sql.Timestamp::from),"code" to code?.trim()),UUID::class.java).filterNotNull()
        val items=ids.map { id ->
            val o=orders.findById(id).orElseThrow();val l=orderLedger.view(id)
            StoreOrderRevenueItemView(id,o.orderCode,o.completedAt,o.commissionRate,o.totalAmount.setScale(0),l.grossCommission,l.commissionAdjustment,l.netCommission,l.grossStorePayable,l.storeAdjustment,l.netStorePayable)
        }
        fun sum(f: (StoreOrderRevenueItemView)->BigDecimal)=items.fold(BigDecimal.ZERO) { s,i -> s+f(i) }.setScale(0)
        return StoreRevenueReportView(StoreRevenueSummaryView(items.size,sum{it.actualSellingPrice},sum{it.grossCommissionAmount},sum{it.commissionAdjustmentAmount},sum{it.netCommissionAmount},sum{it.storeAdjustmentAmount},sum{it.netStoreRevenueAmount}),page(items,page,size))
    }
    fun entitlements(actor: UUID,store: UUID,from: Instant?,to: Instant?,status: String,page: Int,size: Int,sort: String): StoreEntitlementReportView {
        authorize(actor,store);validate(from,to,page,size,sort,setOf("occurredAt"))
        if(status !in setOf("ALL","ELIGIBLE","HELD","DISPUTED","FORFEITED","SETTLED")) throw ApiException(400,"VALIDATION_FAILED","Invalid entitlement status")
        val ids=jdbc.queryForList("""SELECT l.id FROM ledger_entry l JOIN financial_event e ON e.id=l.event_id JOIN customer_order o ON o.id=e.order_id
            WHERE l.store_id=:store AND l.type IN ('STORE_PAYABLE','NO_SHOW_COMPENSATION') AND o.status IN ('COMPLETED','NO_SHOW')
            AND (cast(:from AS timestamptz) IS NULL OR l.occurred_at>=:from) AND (cast(:to AS timestamptz) IS NULL OR l.occurred_at<=:to)
            ORDER BY l.occurred_at DESC,l.id""",mapOf("store" to store,"from" to from?.let(java.sql.Timestamp::from),"to" to to?.let(java.sql.Timestamp::from)),UUID::class.java).filterNotNull()
        val items=ids.map { id ->
            val l=ledger.findById(id).orElseThrow();val o=l.event.order
            val active=jdbc.queryForObject("SELECT count(*) FROM complaint WHERE order_id=:id AND status IN ('OPEN','INVESTIGATING','AWAITING_EVIDENCE','DECIDED')",mapOf("id" to o.id),Int::class.java)!!>0
            val n=if(l.type==LedgerEntryType.NO_SHOW_COMPENSATION) cases.findByOrderId(o.id!!) else null
            val state=if(n!=null) { if(active && n.compensationStatus.name in setOf("ELIGIBLE","FORFEITED")) "DISPUTED" else n.compensationStatus.name } else if(active) "HELD" else "ELIGIBLE"
            val reason=when {
                state=="DISPUTED" -> "OPEN_DISPUTE"
                state=="HELD" && n!=null -> if(n.notificationReceivedAt==null) "AWAITING_NOTIFICATION_RECEIPT" else "WITHIN_DISPUTE_WINDOW"
                state=="HELD" -> "OPEN_COMPLAINT"
                else -> null
            }
            val adj=ledger.findAllByEventOrderIdOrderByOccurredAtAscIdAsc(o.id!!).filter { it.type==LedgerEntryType.ADJUSTMENT && it.originalEntry?.id==id }.fold(BigDecimal.ZERO) { s,e -> s+e.amount }.setScale(0)
            StoreEntitlementItemView(id,o.id!!,o.orderCode,l.type.name,l.amount.setScale(0),adj,(l.amount+adj).setScale(0),state,reason,l.occurredAt)
        }.filter { status=="ALL" || it.eligibilityStatus==status }
        fun sum(type: String,states: Set<String>,gross: Boolean=false)=items.filter { it.type==type && it.eligibilityStatus in states }.fold(BigDecimal.ZERO) { s,i -> s+if(gross) i.grossAmount else i.netAmount }.setScale(0)
        val sales=sum("STORE_PAYABLE",setOf("ELIGIBLE"));val ns=sum("NO_SHOW_COMPENSATION",setOf("ELIGIBLE"))
        val heldNs=sum("NO_SHOW_COMPENSATION",setOf("HELD","DISPUTED"));val heldSales=sum("STORE_PAYABLE",setOf("HELD","DISPUTED"))
        return StoreEntitlementReportView(StoreEntitlementSummaryView(sales,ns,sales+ns,heldNs,heldSales,heldNs+heldSales,sum("NO_SHOW_COMPENSATION",setOf("FORFEITED"),true)),page(items,page,size))
    }
}
