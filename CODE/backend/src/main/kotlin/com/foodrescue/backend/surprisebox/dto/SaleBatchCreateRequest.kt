// AC-01,02 (T4.3): SaleBatchCreateRequest responsibility.
package com.foodrescue.backend.surprisebox.dto

import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Prices default to the template's suggestions. Times are ISO-8601 instants; saleDate is derived in the store timezone. */
data class SaleBatchCreateRequest(
    @field:NotNull val templateId: UUID?,
    val sellingPrice: BigDecimal? = null,
    val minimumValue: BigDecimal? = null,
    @field:NotNull val orderDeadline: Instant?,
    @field:NotNull val pickupStartsAt: Instant?,
    @field:NotNull val pickupEndsAt: Instant?
)
