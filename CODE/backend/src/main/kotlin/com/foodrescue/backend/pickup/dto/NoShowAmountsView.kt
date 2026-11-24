// AC-01..14 (T7.4): NoShowAmountsView responsibility.
package com.foodrescue.backend.pickup.dto
import java.util.UUID
import java.time.Instant
import java.math.BigDecimal
import com.foodrescue.backend.order.dto.AllocatedBoxView
import com.foodrescue.backend.order.dto.OrderMoneyView
import com.foodrescue.backend.payment.dto.RefundRequestView
data class NoShowAmountsView(val retainedAmount: BigDecimal, val refundAmount: BigDecimal,
    val storeCompensationAmount: BigDecimal, val commissionAmount: BigDecimal = BigDecimal.ZERO)
