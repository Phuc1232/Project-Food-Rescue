// AC-01,02 (T4.1): MenuItemCreateRequest responsibility.
package com.foodrescue.backend.catalog.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal

/** active=false creates the item as not yet selling (UC-50 A1). safetyWarnings is required ("Không có" when none). */
data class MenuItemCreateRequest(
    @field:NotBlank @field:Size(max = 255) val name: String?,
    @field:NotBlank @field:Size(max = 64) val categoryCode: String?,
    @field:NotNull val listedPrice: BigDecimal?,
    @field:NotBlank @field:Size(max = 4000) val ingredients: String?,
    @field:NotBlank @field:Size(max = 2000) val safetyWarnings: String?,
    val active: Boolean? = true
)
