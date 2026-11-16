// AC-04 (T6.5): RefundFailRequest responsibility.
package com.foodrescue.backend.payment.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RefundFailRequest(@field:NotBlank @field:Size(max = 1000) val note: String?)
