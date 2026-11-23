// AC-03,04,12 (T7.1): OrderPickupCodeController maps authenticated customer pickup code reads.
package com.foodrescue.backend.pickup.controller

import com.foodrescue.backend.common.security.CurrentAccount
import com.foodrescue.backend.pickup.dto.PickupCodeView
import com.foodrescue.backend.pickup.service.PickupCodeService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/orders")
class OrderPickupCodeController(private val service: PickupCodeService, private val current: CurrentAccount) {
    @GetMapping("/{orderId}/pickup-code")
    fun mine(@PathVariable orderId: UUID): ResponseEntity<PickupCodeView> =
        ResponseEntity.ok().header("Cache-Control", "no-store").body(service.mine(current.get().accountId, orderId))
}
