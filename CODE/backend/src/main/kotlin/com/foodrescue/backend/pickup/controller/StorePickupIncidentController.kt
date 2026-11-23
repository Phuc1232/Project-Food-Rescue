// AC-01..14 (T7.2): StorePickupIncidentController responsibility.
package com.foodrescue.backend.pickup.controller

import com.foodrescue.backend.common.security.CurrentAccount
import com.foodrescue.backend.pickup.service.PickupIncidentService
import com.foodrescue.backend.pickup.dto.PickupIncidentCreateRequest
import com.foodrescue.backend.order.OrderStatus
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/v1/stores/{storeId}")
class StorePickupIncidentController(private val service: PickupIncidentService, private val current: CurrentAccount) {
    @PostMapping("/orders/{orderId}/pickup-incidents")
    @ResponseStatus(HttpStatus.CREATED)
    fun record(@PathVariable storeId: UUID, @PathVariable orderId: UUID,
               @Valid @RequestBody request: PickupIncidentCreateRequest) = service.recordIncident(current.get().accountId, storeId, orderId, request)
}
