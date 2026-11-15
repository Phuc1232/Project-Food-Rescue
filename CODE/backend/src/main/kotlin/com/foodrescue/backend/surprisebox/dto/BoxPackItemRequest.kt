// AC-01,02,03 (T4.4): BoxPackItemRequest responsibility.
package com.foodrescue.backend.surprisebox.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** expectedUnitPrice (optional): the price the packer saw; a different current price returns 409 PRICE_CHANGED (UC-26 E1). */
data class BoxPackItemRequest(
    @field:NotNull val menuItemId: UUID?,
    @field:NotNull @field:Min(1) @field:Max(50) val quantity: Int?,
    @field:NotNull val expiresAt: Instant?,
    val expectedUnitPrice: BigDecimal? = null
)
