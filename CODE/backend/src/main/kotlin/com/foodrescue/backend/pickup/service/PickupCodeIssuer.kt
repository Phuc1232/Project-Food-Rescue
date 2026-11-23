// AC-01,02 (T7.1): PickupCodeIssuer assigns one pickup credential inside the locked order transaction.
package com.foodrescue.backend.pickup.service

import com.foodrescue.backend.order.OrderStatus
import com.foodrescue.backend.order.entity.CustomerOrder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class PickupCodeIssuer(private val codec: PickupCodeCodec) {
    @Transactional(propagation = Propagation.MANDATORY)
    fun issue(order: CustomerOrder): Boolean {
        check(order.status == OrderStatus.CONFIRMED) { "Pickup code requires a confirmed order" }
        if (order.pickupCodeHash != null || order.pickupCodeEncrypted != null || order.pickupCodeKeyId != null) {
            check(order.pickupCodeHash != null && order.pickupCodeEncrypted != null && order.pickupCodeKeyId != null) { "Incomplete pickup code requires operator review" }
            return false
        }
        val code = codec.newCode()
        val encrypted = codec.encrypt(order.id!!, code)
        order.pickupCodeHash = codec.hash(code)
        order.pickupCodeEncrypted = encrypted
        order.pickupCodeKeyId = codec.activeKeyId
        return true
    }
}
