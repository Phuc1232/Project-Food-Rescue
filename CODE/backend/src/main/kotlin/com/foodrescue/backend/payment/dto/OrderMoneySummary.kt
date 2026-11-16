// AC-04,05 (T6.4): OrderMoneySummary responsibility.
package com.foodrescue.backend.payment.dto

import java.math.BigDecimal
import java.util.UUID

/** Admin view of all money of an order: received = applied + refundPending + refunded. */
data class OrderMoneySummary(val orderId: UUID, val orderCode: String, val orderStatus: String, val totalAmount: BigDecimal, val received: BigDecimal,
                             val applied: BigDecimal, val refundPending: BigDecimal, val refunded: BigDecimal, val payments: List<AdminPaymentView>)
