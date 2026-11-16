// AC-02,03 (T4.1): PriceValidator responsibility.
package com.foodrescue.backend.catalog.service

import com.foodrescue.backend.common.response.FieldViolation
import org.springframework.stereotype.Component
import java.math.BigDecimal

/** Listed prices are whole VND between 1 and 100,000,000 (BR-T41-01). */
@Component
class PriceValidator {
    fun check(price: BigDecimal, field: String): FieldViolation? = when {
        price.signum() <= 0 -> FieldViolation(field, "Must be greater than 0")
        price.stripTrailingZeros().scale() > 0 -> FieldViolation(field, "Must be whole VND")
        price > MAX -> FieldViolation(field, "Must be at most 100000000")
        else -> null
    }

    fun normalize(price: BigDecimal): BigDecimal = price.setScale(0)

    private companion object { val MAX = BigDecimal("100000000") }
}
