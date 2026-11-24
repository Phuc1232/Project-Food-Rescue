// AC-01..15 (T7.3): StoreOrderCancelRequest responsibility.
package com.foodrescue.backend.order.dto

import java.util.UUID
import java.time.Instant
import java.math.BigDecimal
import jakarta.validation.constraints.*
import com.foodrescue.backend.order.CancellationDecision
import com.foodrescue.backend.payment.dto.RefundRequestView
import com.foodrescue.backend.pickup.dto.PickupIncidentView

data class StoreOrderCancelRequest(@field:NotBlank @field:Size(max = 1000) val reason: String?,
    val boxesUnsafe: Boolean = true, @field:Size(max = 100) val unsafeBoxIds: List<UUID> = emptyList())
