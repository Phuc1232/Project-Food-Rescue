// AC-03 (T4.2): BoxTemplateUpdateRequest responsibility.
package com.foodrescue.backend.surprisebox.dto

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.util.UUID

/** Send version plus the fields to change. Existing sale batches keep their own snapshot. */
data class BoxTemplateUpdateRequest(
    @field:NotNull val version: Long?,
    @field:Size(max = 255) val name: String? = null,
    @field:Size(max = 64) val categoryCode: String? = null,
    @field:Size(max = 2000) val description: String? = null,
    @field:Size(max = 2000) val safetyWarnings: String? = null,
    val defaultSellingPrice: BigDecimal? = null,
    val defaultMinimumValue: BigDecimal? = null,
    val coverFileId: UUID? = null
)
