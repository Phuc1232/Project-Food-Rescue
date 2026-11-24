// AC-01..15 (T7.3): CustomerCancellationSupplementRequest responsibility.
package com.foodrescue.backend.order.dto

import java.util.UUID
import java.time.Instant
import java.math.BigDecimal
import jakarta.validation.constraints.*
import com.foodrescue.backend.order.CancellationDecision
import com.foodrescue.backend.payment.dto.RefundRequestView
import com.foodrescue.backend.pickup.dto.PickupIncidentView

data class CustomerCancellationSupplementRequest(@field:NotBlank @field:Size(max = 1000) val reason: String?,
    @field:Size(max = 20) val attachmentIds: List<UUID> = emptyList())
