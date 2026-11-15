// AC-08 (T4.3): AdminSaleBatchController responsibility.
package com.foodrescue.backend.surprisebox.controller

import com.foodrescue.backend.common.security.CurrentAccount
import com.foodrescue.backend.surprisebox.dto.SaleBatchBlockRequest
import com.foodrescue.backend.surprisebox.dto.SaleBatchView
import com.foodrescue.backend.surprisebox.service.SaleBatchLifecycleService
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/sale-batches")
@PreAuthorize("hasRole('ADMIN')")
class AdminSaleBatchController(private val lifecycle: SaleBatchLifecycleService, private val current: CurrentAccount) {
    @PostMapping("/{batchId}/block")
    fun block(@PathVariable batchId: UUID, @Valid @RequestBody r: SaleBatchBlockRequest): SaleBatchView =
        lifecycle.block(current.get().accountId, batchId, r.reason!!)
}
