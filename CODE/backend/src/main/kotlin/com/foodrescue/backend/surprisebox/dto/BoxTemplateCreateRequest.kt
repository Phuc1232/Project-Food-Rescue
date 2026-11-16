// AC-01,02 (T4.2): BoxTemplateCreateRequest responsibility.
package com.foodrescue.backend.surprisebox.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.util.UUID

/** Default prices are suggestions for new sale batches (T4.3). Do not list the actual items (BR-05). */
data class BoxTemplateCreateRequest(
    @field:NotBlank @field:Size(max = 255) val name: String?,
    @field:NotBlank @field:Size(max = 64) val categoryCode: String?,
    @field:NotBlank @field:Size(max = 2000) val description: String?,
    @field:NotBlank @field:Size(max = 2000) val safetyWarnings: String?,
    @field:NotNull val defaultSellingPrice: BigDecimal?,
    @field:NotNull val defaultMinimumValue: BigDecimal?,
    val coverFileId: UUID? = null
)
