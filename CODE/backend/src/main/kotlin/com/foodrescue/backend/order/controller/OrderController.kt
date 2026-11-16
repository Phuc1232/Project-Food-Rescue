// AC-01..08 (T5.2): OrderController responsibility.
package com.foodrescue.backend.order.controller

import com.foodrescue.backend.common.security.CurrentAccount
import com.foodrescue.backend.order.dto.*
import com.foodrescue.backend.order.service.OrderPlacementService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/orders")
class OrderController(private val placement: OrderPlacementService, private val current: CurrentAccount) {
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
