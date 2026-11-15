// AC-03,04 (T4.1): MenuItemUpdateRequest responsibility.
package com.foodrescue.backend.catalog.dto

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal

/** Send version plus the fields to change; a new listedPrice requires priceChangeReason (UC-51). */
data class MenuItemUpdateRequest(
    @field:NotNull val version: Long?,
    @field:Size(max = 255) val name: String? = null,
    @field:Size(max = 64) val categoryCode: String? = null,
    val listedPrice: BigDecimal? = null,
    @field:Size(max = 1000) val priceChangeReason: String? = null,
    @field:Size(max = 4000) val ingredients: String? = null,
    @field:Size(max = 2000) val safetyWarnings: String? = null
)
