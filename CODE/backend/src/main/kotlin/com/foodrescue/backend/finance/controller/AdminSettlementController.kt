// AC-01..12 (T8.4): AdminSettlementController responsibility.
package com.foodrescue.backend.finance.controller
import com.foodrescue.backend.finance.service.*
import com.foodrescue.backend.finance.dto.*
import com.foodrescue.backend.finance.SettlementStatus
import com.foodrescue.backend.common.security.CurrentAccount
import org.springframework.web.bind.annotation.*
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.http.HttpStatus
import jakarta.validation.Valid
import java.time.Instant
import java.util.UUID
@RestController
@RequestMapping("/api/v1/admin/finance")
@PreAuthorize("hasRole('ADMIN')")
class AdminSettlementController(private val accounts: PayoutAccountService,private val settlements: SettlementService,
    private val queries: SettlementQueryService,private val payouts: StorePayoutService,private val current: CurrentAccount) {
    @GetMapping("/stores/{storeId}/payout-accounts") fun accounts(@PathVariable storeId: UUID)=accounts.listForAdmin(storeId)
    @GetMapping("/stores/{storeId}/payout-accounts/active-for-payout") fun recipient(@PathVariable storeId: UUID)=accounts.revealActiveForAdminPayout(current.get().accountId,storeId)
    @PostMapping("/stores/{storeId}/payout-accounts/{accountId}/verify") fun verifyAccount(@PathVariable storeId: UUID,@PathVariable accountId: UUID,@Valid @RequestBody r: VerifyPayoutAccountRequest)=accounts.verify(current.get().accountId,storeId,accountId,r)
    @PostMapping("/settlements/draft") fun draft(@Valid @RequestBody r: CreateSettlementDraftRequest)=settlements.createOrRebuildDraft(current.get().accountId,r)
    @PostMapping("/settlements/{settlementId}/confirm") fun confirm(@PathVariable settlementId: UUID)=settlements.confirm(current.get().accountId,settlementId)
    @PostMapping("/settlements/{settlementId}/payouts") @ResponseStatus(HttpStatus.CREATED)
    fun payout(@PathVariable settlementId: UUID,@Valid @RequestBody r: CreateStorePayoutRequest)=payouts.recordPayout(current.get().accountId,settlementId,r)
    @PostMapping("/payouts/{payoutId}/verify") fun verifyPayout(@PathVariable payoutId: UUID,@Valid @RequestBody r: VerifyStorePayoutRequest)=payouts.verifyPayout(current.get().accountId,payoutId,r)
    @PostMapping("/payouts/{payoutId}/fail") fun failPayout(@PathVariable payoutId: UUID,@Valid @RequestBody r: FailStorePayoutRequest)=payouts.failPayout(current.get().accountId,payoutId,r)
    @GetMapping("/settlements/{settlementId}") fun detail(@PathVariable settlementId: UUID)=queries.detailForAdmin(settlementId)
    @GetMapping("/settlements") fun list(@RequestParam(required=false) storeId: UUID?,@RequestParam(required=false) from: Instant?,@RequestParam(required=false) to: Instant?,
        @RequestParam(required=false) status: SettlementStatus?,@RequestParam(defaultValue="0") page: Int,@RequestParam(defaultValue="20") size: Int,
        @RequestParam(defaultValue="periodEndsAt") sort: String)=queries.listForAdmin(storeId,from,to,status,page,size,sort)
}
