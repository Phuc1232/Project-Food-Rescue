// AC-01..08 (T5.2): OrderController responsibility.
package com.foodrescue.backend.order.controller

import com.foodrescue.backend.common.security.CurrentAccount
import com.foodrescue.backend.order.dto.*
import com.foodrescue.backend.common.response.PageResponse
import com.foodrescue.backend.order.OrderStatus
import com.foodrescue.backend.order.service.OrderCancellationService
import com.foodrescue.backend.order.service.OrderPlacementService
import com.foodrescue.backend.order.service.OrderQueryService
import org.springframework.format.annotation.DateTimeFormat
import java.time.Instant
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/orders")
class OrderController(
    private val placement: OrderPlacementService,
    private val queries: OrderQueryService,
    private val cancellation: OrderCancellationService,
    private val current: CurrentAccount
) {
    @GetMapping
    fun mine(@RequestParam(required = false) status: OrderStatus?,
             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant?,
             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant?,
             @RequestParam(defaultValue = "0") page: Int, @RequestParam(defaultValue = "20") size: Int): PageResponse<OrderSummary> =
        queries.mine(current.get().accountId, status, from, to, page, size)

    @GetMapping("/{orderId}/money")
    fun money(@PathVariable orderId: UUID): OrderMoneyView = queries.myMoney(current.get().accountId, orderId)

    @PostMapping("/{orderId}/cancel")
    fun cancel(@PathVariable orderId: UUID, @Valid @RequestBody r: OrderCancelRequest): OrderView =
        cancellation.cancelUnpaid(current.get().accountId, orderId, r.reason!!)

    @PostMapping("/preview")
    fun preview(@Valid @RequestBody r: OrderPreviewRequest): OrderPreview = placement.preview(current.get().accountId, r)

    /** 201 for a new order; 200 when the Idempotency-Key was already used by this customer (same order). */
    @PostMapping
    fun place(@RequestHeader("Idempotency-Key", required = false) key: String?, @Valid @RequestBody r: OrderCreateRequest): ResponseEntity<OrderView> {
        val result = placement.place(current.get().accountId, key, r)
        return ResponseEntity.status(if (result.created) HttpStatus.CREATED else HttpStatus.OK).body(result.order)
    }

    @GetMapping("/{orderId}")
    fun get(@PathVariable orderId: UUID): OrderView = placement.get(current.get().accountId, orderId)
}
