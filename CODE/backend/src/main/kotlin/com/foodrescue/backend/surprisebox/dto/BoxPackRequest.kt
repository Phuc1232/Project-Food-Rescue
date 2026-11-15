// AC-01..06 (T4.4): BoxPackRequest responsibility.
package com.foodrescue.backend.surprisebox.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.util.UUID

/**
 * requestKey: client-generated UUID per packing; resending it returns the same box (UC-26 E3).
 * inspectionPassed: the packer confirms the food, expiry and storage were checked (BR-17); false records a blocked box.
 */
data class BoxPackRequest(
    @field:NotNull val requestKey: UUID?,
    @field:NotEmpty @field:Size(max = 30) @field:Valid val items: List<BoxPackItemRequest>?,
    @field:NotBlank @field:Size(max = 1000) val storageInstructions: String?,
    @field:NotNull val inspectionPassed: Boolean?,
    @field:Size(max = 2000) val inspectionNotes: String? = null
)
