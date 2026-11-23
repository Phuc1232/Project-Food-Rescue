// AC-01..14 (T7.2): StorePickupSessionController responsibility.
package com.foodrescue.backend.pickup.controller

import com.foodrescue.backend.common.security.CurrentAccount
import com.foodrescue.backend.pickup.service.PickupSessionService
import com.foodrescue.backend.pickup.dto.PickupIncidentCreateRequest
import com.foodrescue.backend.order.OrderStatus
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/v1/stores/{storeId}")
class StorePickupSessionController(private val service: PickupSessionService, private val current: CurrentAccount) {
    @PostMapping("/orders/{orderId}/pickup-sessions/start")
    @ResponseStatus(HttpStatus.CREATED)
    fun start(@PathVariable storeId: UUID, @PathVariable orderId: UUID,
              @RequestHeader("Idempotency-Key") key: String) = service.startSession(current.get().accountId, storeId, orderId, key)

    @PostMapping("/pickup-sessions/{sessionId}/complete")
    fun complete(@PathVariable storeId: UUID, @PathVariable sessionId: UUID,
                 @RequestHeader("Idempotency-Key") key: String) = service.completeSession(current.get().accountId, storeId, sessionId, key)
}
