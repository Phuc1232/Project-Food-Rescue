// AC-09 (T4.1): PriceDocumentRequest responsibility.
package com.foodrescue.backend.catalog.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.util.UUID

/** fileId from POST /api/v1/files; menuItemId null = whole menu; replacesDocumentId keeps the old one as replaced (UC-53 A1). */
data class PriceDocumentRequest(
    @field:NotNull val fileId: UUID?,
    val menuItemId: UUID? = null,
    @field:NotBlank @field:Size(max = 1000) val description: String?,
    @field:NotNull val appliesFrom: LocalDate?,
    val replacesDocumentId: UUID? = null
)
