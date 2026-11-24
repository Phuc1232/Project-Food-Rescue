// AC-01..11 (T8.1): ComplaintPaymentView responsibility.
package com.foodrescue.backend.complaint.dto
import java.util.UUID
import java.time.Instant
import java.math.BigDecimal
import com.foodrescue.backend.complaint.*
import com.foodrescue.backend.payment.dto.RefundRequestView
import com.foodrescue.backend.order.dto.*
import com.foodrescue.backend.pickup.dto.*
data class ComplaintPaymentView(val id: UUID, val status: String, val receivedAmount: BigDecimal, val receivedAt: Instant,
    val provider: String, val providerTransactionId: String, val refundedAmount: BigDecimal, val openObligationAmount: BigDecimal, val refundableAmount: BigDecimal)
