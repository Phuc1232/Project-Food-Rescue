// AC-02,03 (T6.1): PaymentLinkRequest responsibility.
package com.foodrescue.backend.payment.gateway

import java.time.Instant

/** amount in whole VND; description is short (payOS: max 9 characters for non-linked bank accounts); expiresAt sent as epoch seconds. */
data class PaymentLinkRequest(val orderCode: Long, val amount: Long, val description: String, val returnUrl: String, val cancelUrl: String,
                              val expiresAt: Instant?) {
    init {
        require(orderCode in 1..MAX_ORDER_CODE) { "orderCode out of range" }
        require(amount > 0) { "amount must be positive" }
    }

    companion object { const val MAX_ORDER_CODE = 9_007_199_254_740_991L } // 2^53 - 1: safe as a JSON number everywhere
}
