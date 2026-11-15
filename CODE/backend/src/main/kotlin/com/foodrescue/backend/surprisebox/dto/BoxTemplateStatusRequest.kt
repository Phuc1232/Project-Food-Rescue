// AC-05 (T4.2): BoxTemplateStatusRequest responsibility.
package com.foodrescue.backend.surprisebox.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class BoxTemplateStatusRequest(@field:NotNull val version: Long?, @field:NotNull val active: Boolean?,
                                    @field:NotBlank @field:Size(max = 1000) val reason: String?)
