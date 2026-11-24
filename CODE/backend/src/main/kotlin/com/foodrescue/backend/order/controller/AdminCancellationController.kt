// AC-01..15 (T7.3): AdminCancellationController responsibility.
package com.foodrescue.backend.order.controller

import com.foodrescue.backend.common.security.CurrentAccount
import com.foodrescue.backend.order.dto.*
import com.foodrescue.backend.order.service.*
import com.foodrescue.backend.order.CancellationRequestStatus
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/cancellation-requests")
@PreAuthorize("hasRole('ADMIN')")
class AdminCancellationController(private val service: AdminCancellationService, private val current: CurrentAccount) {
    @GetMapping
    fun list(@RequestParam(required = false) status: CancellationRequestStatus?, @RequestParam(defaultValue = "0") page: Int,
             @RequestParam(defaultValue = "20") size: Int) = service.list(status, page, size)
    @GetMapping("/{id}")
    fun detail(@PathVariable id: UUID) = service.detail(id)
    @PostMapping("/{id}/decision")
    fun decide(@PathVariable id: UUID, @Valid @RequestBody r: AdminCancellationDecisionRequest,
               @RequestHeader("Idempotency-Key", required = false) key: String?) = service.decide(current.get().accountId, id, r, key)
}
