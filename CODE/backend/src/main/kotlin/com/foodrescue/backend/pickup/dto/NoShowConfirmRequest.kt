// AC-01..14 (T7.4): NoShowConfirmRequest responsibility.
package com.foodrescue.backend.pickup.dto
import java.util.UUID
import java.time.Instant
import java.math.BigDecimal
import com.foodrescue.backend.order.dto.AllocatedBoxView
import com.foodrescue.backend.order.dto.OrderMoneyView
import com.foodrescue.backend.payment.dto.RefundRequestView
data class NoShowConfirmRequest(
    @field:jakarta.validation.constraints.NotNull val storeOpenedOnTime: Boolean?,
    @field:jakarta.validation.constraints.NotNull val boxesReadyAndCompliant: Boolean?,
    @field:jakarta.validation.constraints.NotBlank @field:jakarta.validation.constraints.Size(max = 2000) val evidence: String?,
    @field:jakarta.validation.constraints.Size(max = 10) val attachmentIds: List<UUID> = emptyList())
