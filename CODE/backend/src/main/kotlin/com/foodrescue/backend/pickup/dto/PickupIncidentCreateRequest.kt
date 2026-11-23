// AC-01..14 (T7.2): PickupIncidentCreateRequest responsibility.
package com.foodrescue.backend.pickup.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import jakarta.validation.constraints.NotNull
import java.util.UUID

data class PickupIncidentCreateRequest(
    @field:NotBlank @field:Size(max = 1000) val reason: String,
    @field:NotNull val customerArrived: Boolean?,
    val affectedBoxIds: List<UUID>? = null,
    val resolution: String? = null
)
