// AC-01..12 (T8.4): SettlementQueryService responsibility.
package com.foodrescue.backend.finance.service
import com.foodrescue.backend.finance.dto.*
import com.foodrescue.backend.finance.entity.*
import com.foodrescue.backend.finance.repository.*
import com.foodrescue.backend.finance.*
import com.foodrescue.backend.common.security.StoreAuthorizationService
import com.foodrescue.backend.common.validation.PageQuery
import com.foodrescue.backend.common.response.PageResponse
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.security.access.prepost.PreAuthorize
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Instant
import java.sql.Timestamp
import java.util.UUID
@Service
@Transactional(readOnly=true)
class SettlementQueryService(private val settlements: SettlementRepository,private val lines: SettlementLineRepository,
    private val payouts: StorePayoutRepository,private val authorization: StoreAuthorizationService,
    private val jdbc: NamedParameterJdbcTemplate,private val mapper: ObjectMapper) {
    fun carriedRecovery(s: Settlement): BigDecimal {
        val chain=mutableListOf<Settlement>();val seen=mutableSetOf<UUID>();var previous=s.previousSettlement
        while(previous!=null) {
            if(!seen.add(previous.id!!)) settlementConflict("Invalid settlement recovery chain")
            chain.add(previous);previous=previous.previousSettlement
        }
        // Each confirmed positive period consumes earlier debt once, regardless of its cash payout progress.
        return chain.asReversed().fold(BigDecimal.ZERO) { debt,p -> (debt-p.totalAmount).max(BigDecimal.ZERO) }.setScale(0)
    }
    fun payableCap(s: Settlement)=(s.totalAmount-carriedRecovery(s)).max(BigDecimal.ZERO).setScale(0)
    fun payoutView(p: StorePayout)=StorePayoutView(p.id!!,p.settlement.id!!,p.payoutAccount.id!!,p.payoutAccount.accountVersion,p.recipientSnapshot,p.amount.setScale(0),p.transferReference,p.status.name,p.transferredAt,p.confirmedAt,p.failureReason,p.note,
        jdbc.queryForList("SELECT file_id FROM attachment WHERE payout_id=:id ORDER BY id",mapOf("id" to p.id),UUID::class.java).filterNotNull())
    fun detail(s: Settlement): SettlementDetailView {
        val payments=payouts.findAllBySettlementIdOrderByCreatedAtAscIdAsc(s.id!!)
        val paid=payments.filter { it.status==PayoutStatus.VERIFIED }.fold(BigDecimal.ZERO) { sum,p -> sum+p.amount }.setScale(0)
        val pending=payments.filter { it.status==PayoutStatus.PENDING }.fold(BigDecimal.ZERO) { sum,p -> sum+p.amount }.setScale(0)
        val snapshot=s.heldExceptionsSnapshot?.let { mapper.readValue(it,SettlementSnapshotView::class.java) } ?: SettlementSnapshotView(emptyList(),emptyList())
        val lineViews=lines.findAllBySettlementIdOrderByCreatedAtAscIdAsc(s.id!!).map { line ->
            val e=line.ledgerEntry;val originalSettlement=e.originalEntry?.id?.let { id -> lines.findAllByLedgerEntryIdIn(listOf(id)).firstOrNull()?.settlement?.id }
            SettlementLineView(line.id!!,e.id!!,e.event.order.id!!,e.event.order.orderCode,e.type.name,line.amount.setScale(0),e.originalEntry?.id,originalSettlement)
        }
        val cap=payableCap(s)
        return SettlementDetailView(s.id!!,s.store.id!!,s.settlementCode,s.periodStartsAt,s.periodEndsAt,s.status.name,s.totalAmount.setScale(0),cap,s.totalAmount.negate().max(BigDecimal.ZERO).setScale(0),carriedRecovery(s),paid,pending,(cap-paid-pending).max(BigDecimal.ZERO),s.confirmedAt,s.previousSettlement?.id,s.snapshotTakenAt,lineViews,snapshot.heldItems,snapshot.unresolvedExceptions,payments.map(::payoutView))
    }
    private fun summary(s: Settlement): SettlementSummaryView {
        val d=detail(s)
        return SettlementSummaryView(d.id,d.storeId,d.settlementCode,d.periodStartsAt,d.periodEndsAt,d.status,d.totalAmount,d.payableAmount,d.recoveryAmount,d.carriedOverRecoveryAmount,d.verifiedPaidAmount,d.pendingPayoutAmount,d.remainingPayableAmount,d.confirmedAt)
    }
    fun detailForOwner(actor: UUID,store: UUID,id: UUID): SettlementDetailView {
        authorization.requirePermission(actor,store,"STORE_PROFILE_MANAGE");return detail(settlements.findByIdAndStoreId(id,store) ?: settlementMissing())
    }
    @PreAuthorize("hasRole('ADMIN')")
    fun detailForAdmin(id: UUID)=detail(settlements.findById(id).orElse(null) ?: settlementMissing())
    fun listForOwner(actor: UUID,store: UUID,from: Instant?,to: Instant?,status: SettlementStatus?,page: Int,size: Int,sort: String): PageResponse<SettlementSummaryView> {
        authorization.requirePermission(actor,store,"STORE_PROFILE_MANAGE");return list(store,from,to,status,page,size,sort)
    }
    @PreAuthorize("hasRole('ADMIN')")
    fun listForAdmin(store: UUID?,from: Instant?,to: Instant?,status: SettlementStatus?,page: Int,size: Int,sort: String)=list(store,from,to,status,page,size,sort)
    private fun list(store: UUID?,from: Instant?,to: Instant?,status: SettlementStatus?,page: Int,size: Int,sort: String): PageResponse<SettlementSummaryView> {
        PageQuery().pageable(page,size,"createdAt")
        if(from!=null && to!=null && from>to) settlementInvalid("from must not be after to")
        val column=mapOf("periodEndsAt" to "period_ends_at","confirmedAt" to "confirmed_at","createdAt" to "created_at")[sort] ?: settlementInvalid("Invalid sort")
        val where=""" WHERE (cast(:store AS uuid) IS NULL OR store_id=:store) AND (cast(:status AS text) IS NULL OR status=:status)
            AND (cast(:from AS timestamptz) IS NULL OR period_ends_at>=:from) AND (cast(:to AS timestamptz) IS NULL OR period_ends_at<=:to)"""
        val args=mapOf("store" to store,"status" to status?.name,"from" to from?.let(Timestamp::from),"to" to to?.let(Timestamp::from),"limit" to size,"offset" to page.toLong()*size)
        val count=jdbc.queryForObject("SELECT count(*) FROM settlement$where",args,Long::class.java)!!
        val ids=jdbc.queryForList("SELECT id FROM settlement$where ORDER BY $column DESC NULLS LAST,id LIMIT :limit OFFSET :offset",args,UUID::class.java).filterNotNull()
        return PageResponse(ids.map { summary(settlements.findById(it).orElseThrow()) },page,size,count,((count+size-1)/size).toInt())
    }
}
