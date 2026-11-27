// AC-01..10 (T8.3): AdminFinanceLedgerController responsibility.
package com.foodrescue.backend.finance.controller
import com.foodrescue.backend.finance.service.*
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID
@RestController
@RequestMapping("/api/v1/admin/finance")
@PreAuthorize("hasRole('ADMIN')")
class AdminFinanceLedgerController(private val ledger: OrderLedgerService,private val posting: LedgerPostingService) {
    @GetMapping("/orders/{orderId}/ledger") fun order(@PathVariable orderId: UUID)=ledger.view(orderId)
    @PostMapping("/ledger/reconcile") fun reconcile()=posting.postPendingEvents()
}
