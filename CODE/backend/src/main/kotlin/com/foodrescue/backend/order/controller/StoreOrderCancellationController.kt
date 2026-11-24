// AC-01..15 (T7.3): StoreOrderCancellationController responsibility.
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
@RequestMapping("/api/v1/stores/{storeId}/orders/{orderId}")
class StoreOrderCancellationController(private val service: StoreOrderCancellationService, private val current: CurrentAccount) {
    @PostMapping("/cancel")
    fun cancel(@PathVariable storeId: UUID, @PathVariable orderId: UUID, @Valid @RequestBody r: StoreOrderCancelRequest,
               @RequestHeader("Idempotency-Key", required = false) key: String?) = service.cancelByStore(current.get().accountId, storeId, orderId, r, key)
}
