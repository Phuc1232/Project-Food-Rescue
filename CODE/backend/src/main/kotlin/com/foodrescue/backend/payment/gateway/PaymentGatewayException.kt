// AC-04,05 (T6.1): PaymentGatewayException responsibility.
package com.foodrescue.backend.payment.gateway

/** Typed provider failure. Messages never contain credentials or signatures (BR-T61-02). */
class PaymentGatewayException(val kind: Kind, message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    enum class Kind { NOT_CONFIGURED, TIMEOUT, NETWORK, REJECTED, UNAVAILABLE, INVALID_RESPONSE }
}
