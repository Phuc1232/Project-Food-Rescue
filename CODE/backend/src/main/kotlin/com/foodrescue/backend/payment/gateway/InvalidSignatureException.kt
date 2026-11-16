// AC-01 (T6.1): InvalidSignatureException responsibility.
package com.foodrescue.backend.payment.gateway

/** The webhook signature is missing or does not match; the payload must be ignored entirely. */
class InvalidSignatureException : RuntimeException("Invalid payment webhook signature")
