// AC-05..08 (T7.1): StorePickupCodeController maps credential verification for authorized pickup handlers.
package com.foodrescue.backend.pickup.controller

import com.foodrescue.backend.common.security.CurrentAccount
import com.foodrescue.backend.pickup.dto.PickupCodeVerifyRequest
import com.foodrescue.backend.pickup.dto.PickupVerificationView
import com.foodrescue.backend.pickup.service.PickupCodeService
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/stores/{storeId}/pickup-codes")
class StorePickupCodeController(private val service: PickupCodeService, private val current: CurrentAccount) {
    @PostMapping("/verify")
    fun verify(@PathVariable storeId: UUID, @RequestBody request: PickupCodeVerifyRequest): PickupVerificationView =
        service.verify(current.get().accountId, storeId, request.code)
}
