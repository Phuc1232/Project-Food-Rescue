// AC-05 (T5.3): OrderMoneyView responsibility.
package com.foodrescue.backend.order.dto

import java.math.BigDecimal
import java.util.UUID

/** noShowRetentionIfApplied: what the store would keep under the accepted policy if a no-show is confirmed (BR-42). */
data class OrderMoneyView(val orderId: UUID, val totalAmount: BigDecimal, val verifiedPaid: BigDecimal, val remainingToPay: BigDecimal,
                          val refunded: BigDecimal, val refundInProgress: BigDecimal, val noShowRetentionIfApplied: BigDecimal, val currency: String)
