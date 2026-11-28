// AC-03..07 (T8.5): Admin financial reporting endpoint.
package com.foodrescue.backend.finance.controller
import com.foodrescue.backend.finance.service.AdminFinanceStatsService
import org.springframework.web.bind.annotation.*
import org.springframework.security.access.prepost.PreAuthorize
import java.time.Instant
import java.util.UUID
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/v1/admin/finance/statistics")
class AdminFinanceStatsController(private val service: AdminFinanceStatsService) {
    @GetMapping fun statistics(@RequestParam(required=false) storeId: UUID?,@RequestParam(required=false) from: Instant?,@RequestParam(required=false) to: Instant?)=service.statistics(storeId,from,to)
}
