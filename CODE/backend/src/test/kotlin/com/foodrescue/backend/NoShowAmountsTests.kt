package com.foodrescue.backend

import com.foodrescue.backend.finance.NoShowAmounts
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class NoShowAmountsTests {
    @Test
    fun `no show separates refund from compensation and never charges commission`() {
        val amounts = NoShowAmounts.calculate(BigDecimal("100000"), BigDecimal("100000"), BigDecimal("0.30"))
        assertEquals(BigDecimal("30000"), amounts.retainedAmount)
        assertEquals(BigDecimal("70000"), amounts.refundAmount)
        assertEquals(amounts.retainedAmount, amounts.storeCompensationAmount)
        assertEquals(BigDecimal.ZERO, amounts.commissionAmount)
    }

    @Test
    fun `retention cannot exceed verified payment and VND is rounded half up`() {
        val capped = NoShowAmounts.calculate(BigDecimal("100000"), BigDecimal("20000"), BigDecimal("0.30"))
        assertEquals(BigDecimal("20000"), capped.retainedAmount)
        assertEquals(BigDecimal.ZERO, capped.refundAmount)
        assertEquals(BigDecimal("31"), NoShowAmounts.calculate(BigDecimal("101"), BigDecimal("101"), BigDecimal("0.302")).retainedAmount)
        assertThrows(IllegalArgumentException::class.java) { NoShowAmounts.calculate(BigDecimal("100.5"), BigDecimal("100"), BigDecimal("0.3")) }
        assertThrows(IllegalArgumentException::class.java) { NoShowAmounts.calculate(BigDecimal("100"), BigDecimal("100"), BigDecimal("30")) }
    }
}
