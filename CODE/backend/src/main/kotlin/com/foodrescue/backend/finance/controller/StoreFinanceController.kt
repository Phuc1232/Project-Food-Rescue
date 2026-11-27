// AC-01..10 (T8.3): StoreFinanceController responsibility.
package com.foodrescue.backend.finance.controller
import com.foodrescue.backend.finance.service.StoreFinanceService
import com.foodrescue.backend.common.security.CurrentAccount
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID
@RestController
@RequestMapping("/api/v1/stores/{storeId}/finance")
class StoreFinanceController(private val service: StoreFinanceService, private val current: CurrentAccount) {
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
}
