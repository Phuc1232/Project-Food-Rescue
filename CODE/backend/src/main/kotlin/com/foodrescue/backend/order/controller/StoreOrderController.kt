// AC-06 (T5.3): StoreOrderController responsibility.
package com.foodrescue.backend.order.controller

import com.foodrescue.backend.common.response.PageResponse
import com.foodrescue.backend.common.security.CurrentAccount
import com.foodrescue.backend.order.OrderStatus
import com.foodrescue.backend.order.dto.OrderSummary
import com.foodrescue.backend.order.dto.StoreOrderDetail
import com.foodrescue.backend.order.service.OrderQueryService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/stores/{storeId}/orders")
class StoreOrderController(private val queries: OrderQueryService, private val current: CurrentAccount) {
    @GetMapping
    fun list(@PathVariable storeId: UUID, @RequestParam(required = false) status: OrderStatus?,
             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant?,
             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant?,
             @RequestParam(defaultValue = "0") page: Int, @RequestParam(defaultValue = "20") size: Int): PageResponse<OrderSummary> =
        queries.forStore(current.get().accountId, storeId, status, from, to, page, size)

    @GetMapping("/{orderId}")
    fun detail(@PathVariable storeId: UUID, @PathVariable orderId: UUID): StoreOrderDetail = queries.storeDetail(current.get().accountId, storeId, orderId)
}
