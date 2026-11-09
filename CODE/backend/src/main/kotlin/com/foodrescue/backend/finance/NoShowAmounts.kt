package com.foodrescue.backend.finance

import java.math.BigDecimal
import java.math.RoundingMode

/** VND amounts use whole dong; policy rates are fractions, never percentages. */
@ConsistentCopyVisibility
data class NoShowAmounts private constructor(
    val retainedAmount: BigDecimal,
    val refundAmount: BigDecimal,
    val storeCompensationAmount: BigDecimal,
    val commissionAmount: BigDecimal,
) {
    companion object {
        fun calculate(orderTotal: BigDecimal, verifiedAmount: BigDecimal, retentionRate: BigDecimal): NoShowAmounts {
            require(orderTotal.signum() > 0 && verifiedAmount.signum() >= 0)
            require(orderTotal.stripTrailingZeros().scale() <= 0 && verifiedAmount.stripTrailingZeros().scale() <= 0)
            require(retentionRate >= BigDecimal.ZERO && retentionRate <= BigDecimal.ONE)
            val retained = orderTotal.multiply(retentionRate).setScale(0, RoundingMode.HALF_UP).min(verifiedAmount)
            return NoShowAmounts(retained, verifiedAmount.subtract(retained), retained, BigDecimal.ZERO)
        }
    }
}
