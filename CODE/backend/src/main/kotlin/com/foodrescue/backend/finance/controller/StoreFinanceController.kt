// AC-01..10 (T8.3): StoreFinanceController responsibility.
package com.foodrescue.backend.finance.controller
import com.foodrescue.backend.finance.service.StoreFinanceService
import com.foodrescue.backend.common.security.CurrentAccount
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID
@RestController
@RequestMapping("/api/v1/stores/{storeId}/finance")
class StoreFinanceController(private val service: StoreFinanceService, private val current: CurrentAccount, private val payoutAccounts: com.foodrescue.backend.finance.service.PayoutAccountService, private val settlements: com.foodrescue.backend.finance.service.SettlementQueryService) {
    @GetMapping("/revenue")
    fun revenue(@PathVariable storeId: UUID,@RequestParam(required=false) from: Instant?,@RequestParam(required=false) to: Instant?,
        @RequestParam(required=false) orderCode: String?,@RequestParam(defaultValue="0") page: Int,@RequestParam(defaultValue="20") size: Int,
        @RequestParam(defaultValue="completedAt") sort: String)=service.revenue(current.get().accountId,storeId,from,to,orderCode,page,size,sort)
    @GetMapping("/entitlements")
    fun entitlements(@PathVariable storeId: UUID,@RequestParam(required=false) from: Instant?,@RequestParam(required=false) to: Instant?,
        @RequestParam(defaultValue="ALL") status: String,@RequestParam(defaultValue="0") page: Int,@RequestParam(defaultValue="20") size: Int,
        @RequestParam(defaultValue="occurredAt") sort: String)=service.entitlements(current.get().accountId,storeId,from,to,status,page,size,sort)
    @GetMapping("/orders/{orderId}/ledger")
    fun order(@PathVariable storeId: UUID,@PathVariable orderId: UUID)=service.order(current.get().accountId,storeId,orderId)
    @GetMapping("/payout-accounts")
    fun payoutAccounts(@PathVariable storeId: UUID)=payoutAccounts.listForOwner(current.get().accountId,storeId)
    @PutMapping("/payout-account")
    fun payoutAccount(@PathVariable storeId: UUID,@jakarta.validation.Valid @RequestBody r: com.foodrescue.backend.finance.dto.UpsertPayoutAccountRequest)=payoutAccounts.upsertForOwner(current.get().accountId,storeId,r)
    @GetMapping("/settlements")
    fun settlements(@PathVariable storeId: UUID,@RequestParam(required=false) from: Instant?,@RequestParam(required=false) to: Instant?,
        @RequestParam(required=false) status: com.foodrescue.backend.finance.SettlementStatus?,@RequestParam(defaultValue="0") page: Int,@RequestParam(defaultValue="20") size: Int,
        @RequestParam(defaultValue="periodEndsAt") sort: String)=settlements.listForOwner(current.get().accountId,storeId,from,to,status,page,size,sort)
    @GetMapping("/settlements/{settlementId}")
    fun settlement(@PathVariable storeId: UUID,@PathVariable settlementId: UUID)=settlements.detailForOwner(current.get().accountId,storeId,settlementId)
}
