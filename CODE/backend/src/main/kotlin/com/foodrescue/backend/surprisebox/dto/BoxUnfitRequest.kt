// AC-01..12 (T7.5): BoxUnfitRequest responsibility.
package com.foodrescue.backend.surprisebox.dto
import jakarta.validation.constraints.*
import java.util.UUID
data class BoxUnfitRequest(@field:NotBlank @field:Size(max = 2000) val reason: String?,
    @field:Size(max = 2000) val notes: String? = null, @field:Size(max = 10) val attachmentIds: List<UUID> = emptyList())
