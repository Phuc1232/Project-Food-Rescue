// AC-01..12 (T8.4): PayoutAccountService responsibility.
package com.foodrescue.backend.finance.service
import com.foodrescue.backend.finance.dto.*
import com.foodrescue.backend.finance.entity.StorePayoutAccount
import com.foodrescue.backend.finance.repository.StorePayoutAccountRepository
import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.store.repository.StoreRepository
import com.foodrescue.backend.store.service.StoreHistoryRecorder
import com.foodrescue.backend.common.security.StoreAuthorizationService
import com.foodrescue.backend.notification.service.NotificationOutboxService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.security.access.prepost.PreAuthorize
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.Locale
@Service
class PayoutAccountService(private val accountsRepo: StorePayoutAccountRepository,private val stores: StoreRepository,
    private val actors: AccountRepository,private val authorization: StoreAuthorizationService,private val codec: PayoutAccountCodec,
    private val history: StoreHistoryRecorder,private val outbox: NotificationOutboxService,private val clock: Clock) {
    private fun view(a: StorePayoutAccount)=StorePayoutAccountView(a.id!!,a.store.id!!,a.bankCode,a.accountNumberMasked ?: "******",a.accountHolderName,a.accountVersion,a.verifiedAt,a.effectiveFrom,a.effectiveUntil,a.verificationNote)
    private fun list(storeId: UUID): PayoutAccountHistoryView {
        if(!stores.existsById(storeId)) settlementMissing()
        val versions=accountsRepo.findAllByStoreIdOrderByAccountVersionDesc(storeId).map(::view)
        return PayoutAccountHistoryView(versions.firstOrNull { it.effectiveUntil==null },versions)
    }
    @Transactional(readOnly=true)
    fun listForOwner(actorId: UUID,storeId: UUID): PayoutAccountHistoryView {
        authorization.requirePermission(actorId,storeId,"STORE_PROFILE_MANAGE");return list(storeId)
    }
    @Transactional
    fun upsertForOwner(actorId: UUID,storeId: UUID,r: UpsertPayoutAccountRequest): StorePayoutAccountView {
        authorization.requirePermission(actorId,storeId,"STORE_PROFILE_MANAGE")
        val bank=r.bankCode!!.trim().uppercase(Locale.ROOT);val number=r.accountNumber!!.trim().uppercase(Locale.ROOT);val holder=r.accountHolderName!!.trim()
        if(!bank.matches(Regex("[A-Z0-9-]{2,50}")) || !number.matches(Regex("[A-Z0-9]{6,34}")) || holder.length !in 2..255) settlementInvalid("Invalid payout account")
        val store=stores.findForUpdate(storeId) ?: settlementMissing();val actor=actors.getReferenceById(actorId)
        val active=accountsRepo.findFirstByStoreIdAndEffectiveUntilIsNull(storeId)
        // PostgreSQL requires effectiveUntil strictly after effectiveFrom, including when Clock is fixed.
        val now=maxOf(clock.instant().truncatedTo(ChronoUnit.MICROS),active?.effectiveFrom?.plus(1,ChronoUnit.MICROS) ?: clock.instant().truncatedTo(ChronoUnit.MICROS))
        val before=active?.let { mapOf<String,Any?>("accountVersion" to it.accountVersion,"accountNumberMasked" to it.accountNumberMasked) }
        active?.let { it.effectiveUntil=now;accountsRepo.saveAndFlush(it) }
        val version=accountsRepo.findMaxVersion(storeId)+1
        val saved=accountsRepo.saveAndFlush(StorePayoutAccount(store,bank,codec.encrypt(storeId,version,number),holder,version,effectiveFrom=now,
            keyId=codec.activeKeyId,accountNumberMasked=codec.mask(number),createdBy=actor))
        history.record(actor,store,"STORE_PAYOUT_ACCOUNT",saved.id!!,"PAYOUT_ACCOUNT_UPDATED",before,mapOf("accountVersion" to version,"bankCode" to bank,"accountNumberMasked" to saved.accountNumberMasked))
        return view(saved)
    }
    @PreAuthorize("hasRole('ADMIN')") @Transactional(readOnly=true)
    fun listForAdmin(storeId: UUID)=list(storeId)
    @PreAuthorize("hasRole('ADMIN')") @Transactional
    fun revealActiveForAdminPayout(adminId: UUID,storeId: UUID): AdminPayoutRecipientView {
        val a=accountsRepo.findFirstByStoreIdAndEffectiveUntilIsNull(storeId) ?: settlementMissing()
        val number=codec.decrypt(storeId,a.accountVersion,a.keyId ?: settlementConflict("Recipient encryption key is unavailable"),a.accountNumberCiphertext)
        history.record(actors.getReferenceById(adminId),a.store,"STORE_PAYOUT_ACCOUNT",a.id!!,"PAYOUT_ACCOUNT_REVEALED",null,mapOf("accountVersion" to a.accountVersion,"accountNumberMasked" to a.accountNumberMasked))
        return AdminPayoutRecipientView(a.id!!,storeId,a.bankCode,number,a.accountHolderName,a.accountVersion,a.verifiedAt)
    }
    @PreAuthorize("hasRole('ADMIN')") @Transactional
    fun verify(adminId: UUID,storeId: UUID,id: UUID,r: VerifyPayoutAccountRequest): StorePayoutAccountView {
        stores.findForUpdate(storeId) ?: settlementMissing()
        val a=accountsRepo.findByIdAndStoreId(id,storeId) ?: settlementMissing()
        if(a.effectiveUntil!=null) settlementConflict("Payout account version is superseded")
        if(r.expectedVersion!=null && r.expectedVersion!=a.accountVersion) settlementConflict("Payout account version changed")
        if(a.verifiedAt!=null) return view(a)
        val admin=actors.getReferenceById(adminId);a.verifiedAt=clock.instant();a.verifiedBy=admin;a.verificationNote=r.note?.trim();accountsRepo.saveAndFlush(a)
        history.record(admin,a.store,"STORE_PAYOUT_ACCOUNT",id,"PAYOUT_ACCOUNT_VERIFIED",null,mapOf("accountVersion" to a.accountVersion,"accountNumberMasked" to a.accountNumberMasked))
        outbox.enqueueSettlement(a.store.owner,"PAYOUT_ACCOUNT_VERIFIED","Payout account verified",mapOf("accountId" to id,"accountVersion" to a.accountVersion),dedupeKey="PAYOUT_ACCOUNT_VERIFIED:$id")
        return view(a)
    }
}
