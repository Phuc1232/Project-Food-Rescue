// AC-06,07 (T4.3): SaleBatchUpdateRequest responsibility.
package com.foodrescue.backend.surprisebox.dto

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant

/** Only DRAFT or PAUSED batches; once orders exist only orderDeadline may change (BR-16). */
data class SaleBatchUpdateRequest(
    @field:NotNull val version: Long?,
    val sellingPrice: BigDecimal? = null,
    val minimumValue: BigDecimal? = null,
    val orderDeadline: Instant? = null,
    val pickupStartsAt: Instant? = null,
    val pickupEndsAt: Instant? = null,
    @field:Size(max = 2000) val safetyWarnings: String? = null
)
