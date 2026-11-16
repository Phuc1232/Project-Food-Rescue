// AC-01 (T6.5): RefundApproveRequest responsibility.
package com.foodrescue.backend.payment.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal

/** amount: whole VND to refund (may be less than requested — UC-72 A3). */
data class RefundApproveRequest(@field:NotNull val amount: BigDecimal?, @field:NotBlank @field:Size(max = 1000) val note: String?)
