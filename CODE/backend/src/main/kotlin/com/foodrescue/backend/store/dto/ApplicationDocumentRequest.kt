// AC-01,03: ApplicationDocumentRequest responsibility.
package com.foodrescue.backend.store.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.util.UUID

/** A file uploaded through POST /api/v1/files, classified by the D04 catalogue. */
data class ApplicationDocumentRequest(@field:NotBlank val type: String?, @field:NotNull val fileId: UUID?)
