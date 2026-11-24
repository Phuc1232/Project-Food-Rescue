// AC-01..15 (T7.3): OrderCancellationController responsibility.
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
@RequestMapping("/api/v1/orders/{orderId}")
class OrderCancellationController(private val service: OrderCancellationService, private val current: CurrentAccount) {
    @GetMapping("/cancellation-preview")
    fun preview(@PathVariable orderId: UUID) = service.preview(current.get().accountId, orderId)
    @PostMapping("/cancellation-requests")
    fun submit(@PathVariable orderId: UUID, @Valid @RequestBody r: CustomerCancellationSubmitRequest,
               @RequestHeader("Idempotency-Key", required = false) key: String?): ResponseEntity<OrderCancellationView> {
        val result = service.submitRequest(current.get().accountId, orderId, r, key)
        return ResponseEntity.status(if (result.created) 201 else 200).body(result.request)
    }
    @PostMapping("/cancellation-requests/{requestId}/supplement")
    fun supplement(@PathVariable orderId: UUID, @PathVariable requestId: UUID, @Valid @RequestBody r: CustomerCancellationSupplementRequest) =
        service.supplement(current.get().accountId, orderId, requestId, r)
    @PostMapping("/cancellation-requests/{requestId}/withdraw")
    fun withdraw(@PathVariable orderId: UUID, @PathVariable requestId: UUID) = service.withdraw(current.get().accountId, orderId, requestId)
    @GetMapping("/cancellation")
    fun result(@PathVariable orderId: UUID) = service.customerResult(current.get().accountId, orderId)
}
