// AC-01 (T4.5): MoneyRounding responsibility.
package com.foodrescue.backend.finance.service

import java.math.BigDecimal
import java.math.RoundingMode

/** The single rounding rule for rate-based money (commission, no-show retention): amount × rate, HALF_UP to whole VND (NFR). */
object MoneyRounding {
    const val RULE = "amount x rate, rounded HALF_UP to whole VND"
    fun applyRate(amount: BigDecimal, rate: BigDecimal): BigDecimal = amount.multiply(rate).setScale(0, RoundingMode.HALF_UP)
}
