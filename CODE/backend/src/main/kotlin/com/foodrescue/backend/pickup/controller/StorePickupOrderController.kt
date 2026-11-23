// AC-01..14 (T7.2): StorePickupOrderController responsibility.
package com.foodrescue.backend.pickup.controller

import com.foodrescue.backend.common.security.CurrentAccount
import com.foodrescue.backend.pickup.service.PickupOrderService
import com.foodrescue.backend.pickup.dto.PickupIncidentCreateRequest
import com.foodrescue.backend.order.OrderStatus
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/v1/stores/{storeId}")
class StorePickupOrderController(private val service: PickupOrderService, private val current: CurrentAccount) {
    @GetMapping("/pickup-orders")
    fun list(@PathVariable storeId: UUID, @RequestParam(required = false) date: LocalDate?,
             @RequestParam(required = false) status: OrderStatus?) = service.list(current.get().accountId, storeId, date, status)
}
