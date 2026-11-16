// AC-04 (T5.3): OrderCancelRequest responsibility.
package com.foodrescue.backend.order.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class OrderCancelRequest(@field:NotBlank @field:Size(max = 1000) val reason: String?)
