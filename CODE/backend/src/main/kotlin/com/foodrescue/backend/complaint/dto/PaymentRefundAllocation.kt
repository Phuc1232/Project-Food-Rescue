// AC-01..11 (T8.1): PaymentRefundAllocation responsibility.
package com.foodrescue.backend.complaint.dto
import jakarta.validation.Valid
import jakarta.validation.constraints.*
import com.foodrescue.backend.complaint.*
import java.util.UUID
import java.math.BigDecimal
data class PaymentRefundAllocation(@field:NotNull val paymentId: UUID?=null, @field:NotNull @field:DecimalMin("1") @field:Digits(integer=17,fraction=0) val amount: BigDecimal?=null)
