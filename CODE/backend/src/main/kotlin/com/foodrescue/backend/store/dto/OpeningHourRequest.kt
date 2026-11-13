// AC-01,03: OpeningHourRequest responsibility.
package com.foodrescue.backend.store.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern

/** One shift; dayOfWeek 1 = Monday .. 7 = Sunday; a shift ending at or before its start closes the next day. */
data class OpeningHourRequest(
    @field:NotNull @field:Min(1) @field:Max(7) val dayOfWeek: Int?,
    @field:NotNull @field:Pattern(regexp = TIME) val opensAt: String?,
    @field:NotNull @field:Pattern(regexp = TIME) val closesAt: String?
) { companion object { const val TIME = "^([01][0-9]|2[0-3]):[0-5][0-9]$" } }
