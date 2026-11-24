// AC-01..15 (T7.3): AdminCancellationDecisionRequest responsibility.
package com.foodrescue.backend.order.dto

import java.util.UUID
import java.time.Instant
import java.math.BigDecimal
import jakarta.validation.constraints.*
import com.foodrescue.backend.order.CancellationDecision
import com.foodrescue.backend.payment.dto.RefundRequestView
import com.foodrescue.backend.pickup.dto.PickupIncidentView

data class AdminCancellationDecisionRequest(@field:NotNull val decision: CancellationDecision?,
    @field:NotBlank @field:Size(max = 1000) val reason: String?, val refundAmount: BigDecimal? = null,
    val storeFault: Boolean = false, val releaseBoxesToAvailable: Boolean = false)
