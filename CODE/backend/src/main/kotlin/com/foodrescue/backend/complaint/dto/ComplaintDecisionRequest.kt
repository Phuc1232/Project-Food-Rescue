// AC-01..11 (T8.1): ComplaintDecisionRequest responsibility.
package com.foodrescue.backend.complaint.dto
import jakarta.validation.Valid
import jakarta.validation.constraints.*
import com.foodrescue.backend.complaint.*
import java.util.UUID
import java.math.BigDecimal
data class ComplaintDecisionRequest(@field:NotNull val outcome: ComplaintOutcome?=null, @field:NotNull val responsibility: ComplaintResponsibility?=null,
    @field:NotBlank @field:Size(max=10000) val reason: String?=null, @field:Valid @field:Size(max=100) val refundAllocations: List<PaymentRefundAllocation> = emptyList(),
    @field:Digits(integer=17,fraction=0) val storeAdjustmentAmount: BigDecimal?=null, @field:Digits(integer=17,fraction=0) val commissionAdjustmentAmount: BigDecimal?=null)
