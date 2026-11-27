// AC-01..12 (T8.4): StorePayoutService responsibility.
package com.foodrescue.backend.finance.service
import com.foodrescue.backend.finance.*
import com.foodrescue.backend.finance.entity.*
import com.foodrescue.backend.finance.dto.*
import com.foodrescue.backend.finance.repository.*
import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.store.repository.StoreRepository
import com.foodrescue.backend.store.service.StoreHistoryRecorder
import com.foodrescue.backend.file.entity.Attachment
import com.foodrescue.backend.file.entity.StoredFile
import com.foodrescue.backend.file.repository.AttachmentRepository
import com.foodrescue.backend.notification.service.NotificationOutboxService
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.security.access.prepost.PreAuthorize
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.sql.Timestamp
import java.util.UUID
@Service
@PreAuthorize("hasRole('ADMIN')")
class StorePayoutService(private val payouts: StorePayoutRepository,private val settlements: SettlementRepository,
    private val stores: StoreRepository,private val accounts: StorePayoutAccountRepository,private val actors: AccountRepository,
    private val attachments: AttachmentRepository,private val em: EntityManager,private val jdbc: NamedParameterJdbcTemplate,
    private val queries: SettlementQueryService,private val mapper: ObjectMapper,private val history: StoreHistoryRecorder,
    private val outbox: NotificationOutboxService,private val clock: Clock) {
    private fun lockSettlement(id: UUID): Settlement {
        val store=jdbc.queryForList("SELECT store_id FROM settlement WHERE id=:id",mapOf("id" to id),UUID::class.java).firstOrNull() ?: settlementMissing()
        stores.findForUpdate(store) ?: settlementMissing();return settlements.findForUpdate(id) ?: settlementMissing()
    }
    private fun evidence(admin: UUID,id: UUID): StoredFile {
        val f=em.find(StoredFile::class.java,id,LockModeType.PESSIMISTIC_WRITE)?.takeIf { it.uploadedBy.id==admin }
            ?: settlementInvalid("Evidence file not found or not owned by administrator")
        if(attachments.findAllByFileId(id).isNotEmpty()) settlementInvalid("Evidence file is already attached")
        return f
    }
    private fun active(s: Settlement,id: UUID,version: Int): StorePayoutAccount {
        val a=accounts.findFirstByStoreIdAndEffectiveUntilIsNull(s.store.id!!) ?: settlementConflict("No active payout account")
        if(a.id!=id || a.accountVersion!=version || a.verifiedAt==null) settlementConflict("Payout account changed or unverified; verify recipient before payout")
        return a
    }
    private fun committed(id: UUID)=payouts.findAllBySettlementIdOrderByCreatedAtAscIdAsc(id).filter { it.status in setOf(PayoutStatus.PENDING,PayoutStatus.VERIFIED) }.fold(BigDecimal.ZERO) { sum,p -> sum+p.amount }
    private fun recalculate(s: Settlement) {
        val paid=payouts.findAllBySettlementIdOrderByCreatedAtAscIdAsc(s.id!!).filter { it.status==PayoutStatus.VERIFIED }.fold(BigDecimal.ZERO) { sum,p -> sum+p.amount }
        val cap=queries.payableCap(s)
        s.status=if(cap.signum()>0 && paid>=cap) SettlementStatus.PAID else if(paid.signum()>0) SettlementStatus.PARTIALLY_PAID else SettlementStatus.CONFIRMED
        settlements.saveAndFlush(s)
    }
    private fun record(p: StorePayout,adminId: UUID,action: String) {
        history.record(actors.getReferenceById(adminId),p.store,"STORE_PAYOUT",p.id!!,action,null,mapOf("settlementId" to p.settlement.id,"amount" to p.amount,"status" to p.status,"accountVersion" to p.payoutAccount.accountVersion))
        if(action=="PAYOUT_VERIFIED") outbox.enqueueSettlement(p.store.owner,"STORE_PAYOUT_VERIFIED","Store payout verified",mapOf("payoutId" to p.id,"settlementId" to p.settlement.id,"amount" to p.amount),dedupeKey="STORE_PAYOUT_VERIFIED:${p.id}")
    }
    @Transactional
    fun recordPayout(adminId: UUID,id: UUID,r: CreateStorePayoutRequest): StorePayoutView {
        val s=lockSettlement(id)
        if(s.status !in setOf(SettlementStatus.CONFIRMED,SettlementStatus.PARTIALLY_PAID)) settlementConflict("Settlement must be confirmed and not fully paid")
        if(s.totalAmount.signum()<=0) settlementConflict("Negative or zero settlement cannot be paid out; recovery required")
        val a=active(s,r.payoutAccountId!!,r.expectedAccountVersion!!)
        val amount=r.amount!!
        if(amount.signum()<=0 || amount.stripTrailingZeros().scale()>0) settlementInvalid("Amount must be positive whole VND")
        if(amount>queries.payableCap(s)-committed(id)) settlementConflict("Payout amount exceeds remaining eligible amount")
        if(r.status !in setOf(PayoutStatus.PENDING,PayoutStatus.VERIFIED)) settlementInvalid("New payout must be PENDING or VERIFIED")
        val ref=r.transferReference!!.trim();if(ref.isEmpty()) settlementInvalid("Transfer reference must not be blank")
        if(payouts.existsByTransferReference(ref)) settlementConflict("Transfer reference is already recorded")
        if(r.status==PayoutStatus.VERIFIED && (r.transferredAt==null || r.evidenceFileId==null)) settlementInvalid("Verified payout requires transferredAt and evidenceFileId")
        if(r.transferredAt!=null && r.transferredAt>clock.instant()) settlementInvalid("transferredAt must not be in the future")
        val file=r.evidenceFileId?.let { evidence(adminId,it) }
        val snapshot=mapper.writeValueAsString(mapOf("accountVersion" to a.accountVersion,"bankCode" to a.bankCode,"accountNumberMasked" to a.accountNumberMasked,"accountHolderName" to a.accountHolderName,"verifiedAt" to a.verifiedAt))
        val payoutId=UUID.randomUUID();val now=clock.instant();em.flush()
        // Global reference uniqueness must return 409 across different store locks as well.
        val inserted=jdbc.update("""INSERT INTO store_payout(id,created_at,updated_at,version,store_id,settlement_id,payout_account_id,recipient_snapshot,amount,transfer_reference,status,confirmed_by_id,confirmed_at,transferred_at,recorded_by_id,note)
            VALUES (:id,:now,:now,0,:store,:settlement,:account,:snapshot,:amount,:reference,:status,:confirmedBy,:confirmedAt,:transferred,:actor,:note)
            ON CONFLICT (transfer_reference) DO NOTHING""",mapOf("id" to payoutId,"now" to Timestamp.from(now),"store" to s.store.id,"settlement" to id,"account" to a.id,
                "snapshot" to snapshot,"amount" to amount.setScale(0),"reference" to ref,"status" to r.status.name,"confirmedBy" to if(r.status==PayoutStatus.VERIFIED) adminId else null,
                "confirmedAt" to if(r.status==PayoutStatus.VERIFIED) Timestamp.from(now) else null,"transferred" to r.transferredAt?.let(Timestamp::from),"actor" to adminId,"note" to r.note?.trim()))
        if(inserted==0) settlementConflict("Transfer reference is already recorded")
        val p=payouts.findById(payoutId).orElseThrow()
        file?.let { attachments.saveAndFlush(Attachment(it,payout=p)) }
        recalculate(s);record(p,adminId,"PAYOUT_RECORDED");if(p.status==PayoutStatus.VERIFIED) record(p,adminId,"PAYOUT_VERIFIED")
        return queries.payoutView(p)
    }
    private fun lockPayout(id: UUID): StorePayout {
        val settlementId=jdbc.queryForList("SELECT settlement_id FROM store_payout WHERE id=:id",mapOf("id" to id),UUID::class.java).firstOrNull() ?: settlementMissing();lockSettlement(settlementId)
        return payouts.findForUpdate(id) ?: settlementMissing()
    }
    @Transactional
    fun verifyPayout(adminId: UUID,id: UUID,r: VerifyStorePayoutRequest): StorePayoutView {
        val p=lockPayout(id);if(p.status!=PayoutStatus.PENDING) settlementConflict("Payout must be PENDING")
        active(p.settlement,p.payoutAccount.id!!,p.payoutAccount.accountVersion)
        if(committed(p.settlement.id!!)>queries.payableCap(p.settlement)) settlementConflict("Payout commitments exceed settlement limit")
        val transferred=r.transferredAt ?: p.transferredAt ?: settlementInvalid("transferredAt is required")
        if(transferred>clock.instant()) settlementInvalid("transferredAt must not be in the future")
        val existingEvidence=jdbc.queryForList("SELECT file_id FROM attachment WHERE payout_id=:id",mapOf("id" to id),UUID::class.java).filterNotNull()
        if(existingEvidence.isEmpty() && r.evidenceFileId==null) settlementInvalid("evidenceFileId is required")
        r.evidenceFileId?.takeUnless { it in existingEvidence }?.let { attachments.saveAndFlush(Attachment(evidence(adminId,it),payout=p)) }
        p.status=PayoutStatus.VERIFIED;p.confirmedBy=actors.getReferenceById(adminId);p.confirmedAt=clock.instant();p.transferredAt=transferred;p.note=r.note?.trim() ?: p.note
        payouts.saveAndFlush(p);recalculate(p.settlement);record(p,adminId,"PAYOUT_VERIFIED");return queries.payoutView(p)
    }
    @Transactional
    fun failPayout(adminId: UUID,id: UUID,r: FailStorePayoutRequest): StorePayoutView {
        val p=lockPayout(id);if(p.status!=PayoutStatus.PENDING) settlementConflict("Payout must be PENDING")
        val reason=r.reason!!.trim();if(reason.isEmpty()) settlementInvalid("Failure reason is required")
        p.status=PayoutStatus.FAILED;p.failureReason=reason;payouts.saveAndFlush(p);recalculate(p.settlement);record(p,adminId,"PAYOUT_FAILED");return queries.payoutView(p)
    }
}
