// AC-02,03,05 (T6.5): RefundConfirmRequest responsibility.
package com.foodrescue.backend.payment.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

/** Recorded after the administrator made the bank transfer (D3). bankTransactionId is never reused; evidence is a file uploaded through POST /api/v1/files. */
data class RefundConfirmRequest(
    @field:NotBlank @field:Pattern(regexp = "^[A-Za-z0-9._-]{4,64}$") val bankTransactionId: String?,
    @field:NotNull val transferredAt: Instant?,
    @field:NotNull val evidenceFileId: UUID?,
    @field:Size(max = 1000) val note: String? = null
)
