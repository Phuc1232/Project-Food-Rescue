// AC-03..07 (T8.5): Snapshot-consistent financial metrics with disjoint ledger classification and shared recovery rules.
package com.foodrescue.backend.finance.service
import com.foodrescue.backend.finance.dto.*
import com.foodrescue.backend.finance.repository.SettlementRepository
import com.foodrescue.backend.store.repository.StoreRepository
import com.foodrescue.backend.order.OrderStatus
import com.foodrescue.backend.common.exception.ApiException
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.annotation.Isolation
import org.springframework.security.access.prepost.PreAuthorize
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
@Service
@PreAuthorize("hasRole('ADMIN')")
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
class AdminFinanceStatsService(private val stores: StoreRepository,private val settlements: SettlementRepository,
    private val queries: SettlementQueryService,private val jdbc: NamedParameterJdbcTemplate,private val mapper: ObjectMapper) {
    private val ledgerNames=listOf("verifiedCollectedAmount","verifiedRefundedAmount","netCashFlowAmount","completedOrderGrossRevenue","grossCommissionAmount","commissionAdjustmentAmount","netCommissionAmount","grossStorePayableAmount","storePayableAdjustmentAmount","netStorePayableAmount","grossNoShowCompensationAmount","noShowAdjustmentAmount","netNoShowCompensationAmount","totalStoreEntitlementNetAmount","totalLedgerAllocatedAmount","completedOrderCount")
    private val settlementNames=listOf("heldNoShowAmount","heldDisputedSalesAmount","heldAmount","draftUnconfirmedAmount","eligibleUnassignedAmount","eligibleUnsettledAmount","confirmedSettledLedgerAmount","confirmedSettlementTotalAmount","confirmedSettlementPayableAmount","confirmedSettlementRecoveryAmount","paidOutVerifiedAmount","pendingPayoutAmount","confirmedRemainingPayableAmount","outstandingRecoveryDebtAmount","totalEstimatedPayableAmount","confirmedSettlementCount")
    private fun zeros(names: List<String>)=names.associateWith { BigDecimal.ZERO }.toMutableMap()
    private fun MutableMap<String,BigDecimal>.add(key: String,amount: BigDecimal) {this[key]=getValue(key)+amount.setScale(0)}
    private fun counts()=OrderStatus.entries.associate { it.name to 0L }.toMutableMap()
    private data class Metrics(val orders: Map<String,Long>,val ledger: Map<String,BigDecimal>,val settlement: Map<String,BigDecimal>)
    private fun calculate(storeId: UUID,from: Instant?,to: Instant?): Metrics {
        val args=mapOf("store" to storeId,"from" to from?.let(Timestamp::from),"to" to to?.let(Timestamp::from))
        val orderCounts=counts()
        jdbc.query("""SELECT status,count(*) n FROM customer_order WHERE store_id=:store
            AND (cast(:from AS timestamptz) IS NULL OR accepted_at>=:from) AND (cast(:to AS timestamptz) IS NULL OR accepted_at<=:to) GROUP BY status""",args) {rs,_ -> rs.getString("status") to rs.getLong("n")}.forEach { (status,n) -> orderCounts[status]=n }
        val l=zeros(ledgerNames);val h=zeros(settlementNames)
        val completed=jdbc.queryForMap("""SELECT count(*) n,coalesce(sum(total_amount),0) amount FROM customer_order WHERE store_id=:store AND status='COMPLETED'
            AND (cast(:from AS timestamptz) IS NULL OR completed_at>=:from) AND (cast(:to AS timestamptz) IS NULL OR completed_at<=:to)""",args)
        l["completedOrderCount"]=BigDecimal((completed["n"] as Number).toLong());l["completedOrderGrossRevenue"]=(completed["amount"] as BigDecimal).setScale(0)
        val entries=jdbc.queryForList("""SELECT l.type,l.amount,original.type original_type,o.status order_status,n.compensation_status,s.status settlement_status,
            EXISTS(SELECT 1 FROM complaint c WHERE c.order_id=o.id AND c.status IN ('OPEN','INVESTIGATING','AWAITING_EVIDENCE','DECIDED')) disputed
            FROM ledger_entry l JOIN financial_event e ON e.id=l.event_id JOIN customer_order o ON o.id=e.order_id
            LEFT JOIN ledger_entry original ON original.id=l.original_entry_id LEFT JOIN no_show_case n ON n.order_id=o.id
            LEFT JOIN settlement_line sl ON sl.ledger_entry_id=l.id LEFT JOIN settlement s ON s.id=sl.settlement_id
            WHERE l.store_id=:store AND (cast(:from AS timestamptz) IS NULL OR l.occurred_at>=:from) AND (cast(:to AS timestamptz) IS NULL OR l.occurred_at<=:to)""",args)
        entries.forEach { e ->
            val type=e["type"] as String;val amount=(e["amount"] as BigDecimal).setScale(0);val original=e["original_type"] as? String
            when(type) {
                "PAYMENT_RECEIPT" -> l.add("verifiedCollectedAmount",amount)
                "CUSTOMER_REFUND" -> l.add("verifiedRefundedAmount",amount.negate())
                "PLATFORM_COMMISSION" -> l.add("grossCommissionAmount",amount)
                "STORE_PAYABLE" -> l.add("grossStorePayableAmount",amount)
                "NO_SHOW_COMPENSATION" -> l.add("grossNoShowCompensationAmount",amount)
                "ADJUSTMENT" -> when(original) {"PLATFORM_COMMISSION" -> l.add("commissionAdjustmentAmount",amount);"STORE_PAYABLE" -> l.add("storePayableAdjustmentAmount",amount);"NO_SHOW_COMPENSATION" -> l.add("noShowAdjustmentAmount",amount)}
            }
            val base=if(type=="ADJUSTMENT") original else type
            if(base in setOf("STORE_PAYABLE","NO_SHOW_COMPENSATION")) {
                val bucket=when {
                    e["settlement_status"] in setOf("CONFIRMED","PARTIALLY_PAID","PAID") -> "confirmedSettledLedgerAmount"
                    e["settlement_status"]=="DRAFT" -> "draftUnconfirmedAmount"
                    e["disputed"]==true || (e["order_status"]=="NO_SHOW" && e["compensation_status"] in setOf("HELD","DISPUTED")) -> if(base=="NO_SHOW_COMPENSATION") "heldNoShowAmount" else "heldDisputedSalesAmount"
                    else -> "eligibleUnassignedAmount"
                }
                h.add(bucket,amount)
            }
        }
        l["netCashFlowAmount"]=l.getValue("verifiedCollectedAmount")-l.getValue("verifiedRefundedAmount")
        l["netCommissionAmount"]=l.getValue("grossCommissionAmount")+l.getValue("commissionAdjustmentAmount")
        l["netStorePayableAmount"]=l.getValue("grossStorePayableAmount")+l.getValue("storePayableAdjustmentAmount")
        l["netNoShowCompensationAmount"]=l.getValue("grossNoShowCompensationAmount")+l.getValue("noShowAdjustmentAmount")
        l["totalStoreEntitlementNetAmount"]=l.getValue("netStorePayableAmount")+l.getValue("netNoShowCompensationAmount")
        l["totalLedgerAllocatedAmount"]=l.getValue("netCommissionAmount")+l.getValue("totalStoreEntitlementNetAmount")
        h["heldAmount"]=h.getValue("heldNoShowAmount")+h.getValue("heldDisputedSalesAmount")
        h["eligibleUnsettledAmount"]=h.getValue("draftUnconfirmedAmount")+h.getValue("eligibleUnassignedAmount")
        val ids=jdbc.queryForList("SELECT id FROM settlement WHERE store_id=:store AND status<>'DRAFT' ORDER BY period_ends_at,created_at,id",args,UUID::class.java).filterNotNull()
        val headers=settlements.findAllById(ids).associateBy {it.id}
        fun within(at: Instant?)=at!=null && (from==null || at>=from) && (to==null || at<=to)
        val payments=jdbc.queryForList("SELECT settlement_id,amount,status,transferred_at,confirmed_at,created_at FROM store_payout WHERE store_id=:store",args)
        val committed=payments.filter {it["status"] in setOf("PENDING","VERIFIED")}.groupBy {it["settlement_id"]}.mapValues { (_,rows) -> rows.fold(BigDecimal.ZERO) {sum,p -> sum+(p["amount"] as BigDecimal)} }
        val selected=ids.mapNotNull {headers[it]}.filter { (from==null && to==null) || within(it.confirmedAt) }
        selected.forEach { s ->
            h.add("confirmedSettlementCount",BigDecimal.ONE);h.add("confirmedSettlementTotalAmount",s.totalAmount)
            val cap=queries.payableCap(s)
            h.add("confirmedSettlementPayableAmount",cap);h.add("confirmedSettlementRecoveryAmount",s.totalAmount.negate().max(BigDecimal.ZERO))
            h.add("confirmedRemainingPayableAmount",(cap-(committed[s.id] ?: BigDecimal.ZERO)).max(BigDecimal.ZERO))
        }
        selected.lastOrNull()?.let { h["outstandingRecoveryDebtAmount"]=(queries.carriedRecovery(it)-it.totalAmount).max(BigDecimal.ZERO).setScale(0) }
        payments.forEach { p ->
            val at=when(p["status"]) {"VERIFIED" -> (p["transferred_at"] ?: p["confirmed_at"]) as? Timestamp;"PENDING" -> (p["transferred_at"] ?: p["created_at"]) as? Timestamp;else -> null}
            if((from==null && to==null) || within(at?.toInstant())) when(p["status"]) {"VERIFIED" -> h.add("paidOutVerifiedAmount",p["amount"] as BigDecimal);"PENDING" -> h.add("pendingPayoutAmount",p["amount"] as BigDecimal)}
        }
        h["totalEstimatedPayableAmount"]=h.getValue("eligibleUnsettledAmount")+h.getValue("confirmedRemainingPayableAmount")
        check(l.getValue("totalStoreEntitlementNetAmount").compareTo(h.getValue("heldAmount")+h.getValue("eligibleUnsettledAmount")+h.getValue("confirmedSettledLedgerAmount"))==0)
        return Metrics(orderCounts,l,h)
    }
    fun statistics(storeId: UUID?,from: Instant?,to: Instant?): AdminFinanceStatisticsView {
        if(from!=null && to!=null && from>to) throw ApiException(400,"VALIDATION_FAILED","from must not be after to")
        val scope=if(storeId!=null) listOf(stores.findById(storeId).orElse(null) ?: throw ApiException(404,"NOT_FOUND","Store not found")) else stores.findAll().sortedWith(compareBy({it.name},{it.id}))
        val orders=counts();val ledger=zeros(ledgerNames);val settlement=zeros(settlementNames)
        val byStore=scope.map { s ->
            val m=calculate(s.id!!,from,to);m.orders.forEach { (k,v) -> orders[k]=orders.getValue(k)+v };m.ledger.forEach { (k,v) -> ledger.add(k,v) };m.settlement.forEach { (k,v) -> settlement.add(k,v) }
            AdminStoreFinanceStatsItem(s.id!!,s.name,s.status.name,m.orders.values.sum(),m.orders,mapper.convertValue(m.ledger,AdminFinanceLedgerStatsView::class.java),mapper.convertValue(m.settlement,AdminFinanceSettlementStatsView::class.java))
        }
        return AdminFinanceStatisticsView(storeId,from,to,orders.values.sum(),orders,mapper.convertValue(ledger,AdminFinanceLedgerStatsView::class.java),mapper.convertValue(settlement,AdminFinanceSettlementStatsView::class.java),byStore)
    }
}
