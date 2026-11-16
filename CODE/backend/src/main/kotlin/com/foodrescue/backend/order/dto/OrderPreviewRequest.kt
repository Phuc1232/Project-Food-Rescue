// AC-01 (T5.2): OrderPreviewRequest responsibility.
package com.foodrescue.backend.order.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import java.util.UUID

data class OrderPreviewRequest(@field:NotNull val batchId: UUID?, @field:NotNull @field:Min(1) val quantity: Int?)
