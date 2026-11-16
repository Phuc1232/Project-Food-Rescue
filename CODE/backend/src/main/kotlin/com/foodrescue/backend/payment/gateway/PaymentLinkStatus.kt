// AC-03 (T6.1): PaymentLinkStatus responsibility.
package com.foodrescue.backend.payment.gateway

/** Provider link states. UNKNOWN for anything unrecognised — never treated as paid. */
enum class PaymentLinkStatus {
    PENDING, PROCESSING, PAID, UNDERPAID, CANCELLED, EXPIRED, UNKNOWN;

    companion object { fun parse(v: String?) = entries.firstOrNull { it.name == v?.uppercase() } ?: UNKNOWN }
}
