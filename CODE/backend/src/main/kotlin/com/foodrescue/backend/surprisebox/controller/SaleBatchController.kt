// AC-01..09 (T4.3): SaleBatchController responsibility.
package com.foodrescue.backend.surprisebox.controller

import com.foodrescue.backend.common.security.CurrentAccount
import com.foodrescue.backend.surprisebox.SaleBatchStatus
import com.foodrescue.backend.surprisebox.dto.*
import com.foodrescue.backend.surprisebox.service.SaleBatchLifecycleService
import com.foodrescue.backend.surprisebox.service.SaleBatchService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/stores/{storeId}/sale-batches")
class SaleBatchController(private val batches: SaleBatchService, private val lifecycle: SaleBatchLifecycleService, private val current: CurrentAccount) {
    @GetMapping
    fun list(@PathVariable storeId: UUID, @RequestParam(required = false) status: SaleBatchStatus?): List<SaleBatchView> =
        batches.list(current.get().accountId, storeId, status)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@PathVariable storeId: UUID, @Valid @RequestBody r: SaleBatchCreateRequest): SaleBatchView = batches.create(current.get().accountId, storeId, r)

    @GetMapping("/{batchId}")
    fun get(@PathVariable storeId: UUID, @PathVariable batchId: UUID): SaleBatchView = batches.get(current.get().accountId, storeId, batchId)

    @PatchMapping("/{batchId}")
    fun update(@PathVariable storeId: UUID, @PathVariable batchId: UUID, @Valid @RequestBody r: SaleBatchUpdateRequest): SaleBatchView =
        batches.update(current.get().accountId, storeId, batchId, r)

    @PostMapping("/{batchId}/status")
    fun status(@PathVariable storeId: UUID, @PathVariable batchId: UUID, @Valid @RequestBody r: SaleBatchStatusRequest): SaleBatchView =
        lifecycle.apply(current.get().accountId, storeId, batchId, r.action!!)

    @GetMapping("/{batchId}/box-counts")
    fun counts(@PathVariable storeId: UUID, @PathVariable batchId: UUID): BoxCountsView = batches.counts(current.get().accountId, storeId, batchId)
}
