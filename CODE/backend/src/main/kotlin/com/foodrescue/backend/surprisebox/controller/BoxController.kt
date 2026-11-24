// AC-01..09 (T4.4): BoxController responsibility.
package com.foodrescue.backend.surprisebox.controller

import com.foodrescue.backend.common.security.CurrentAccount
import com.foodrescue.backend.surprisebox.BoxStatus
import com.foodrescue.backend.surprisebox.dto.*
import com.foodrescue.backend.surprisebox.service.BoxPackingService
import com.foodrescue.backend.surprisebox.service.BoxQueryService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/stores/{storeId}")
class BoxController(private val packing: BoxPackingService, private val queries: BoxQueryService, private val current: CurrentAccount, private val recheck: com.foodrescue.backend.surprisebox.service.BoxRecheckService) {
    @GetMapping("/boxes/awaiting-inspection")
    fun awaiting(@PathVariable storeId: UUID) = recheck.listAwaitingInspection(current.get().accountId, storeId)

    @PostMapping("/boxes/{boxId}/recheck")
    fun recheck(@PathVariable storeId: UUID, @PathVariable boxId: UUID, @Valid @RequestBody r: BoxRecheckRequest) =
        recheck.recheck(current.get().accountId, storeId, boxId, r)

    /** 201 for a new box; 200 when the requestKey was already used (same box returned). */
    @PostMapping("/sale-batches/{batchId}/boxes")
    fun pack(@PathVariable storeId: UUID, @PathVariable batchId: UUID, @Valid @RequestBody r: BoxPackRequest): ResponseEntity<BoxDetail> {
        val result = packing.pack(current.get().accountId, storeId, batchId, r)
        return ResponseEntity.status(if (result.created) HttpStatus.CREATED else HttpStatus.OK).body(result.box)
    }

    @GetMapping("/sale-batches/{batchId}/boxes")
    fun list(@PathVariable storeId: UUID, @PathVariable batchId: UUID, @RequestParam(required = false) status: BoxStatus?): List<BoxView> =
        queries.list(current.get().accountId, storeId, batchId, status)

    @GetMapping("/boxes/{boxId}")
    fun detail(@PathVariable storeId: UUID, @PathVariable boxId: UUID): BoxDetail = queries.detail(current.get().accountId, storeId, boxId)

    @GetMapping("/boxes/{boxId}/prefill")
    fun prefill(@PathVariable storeId: UUID, @PathVariable boxId: UUID): List<PrefillItemView> = queries.prefill(current.get().accountId, storeId, boxId)
}
