// AC-01..14 (T7.4): BoxRecheckRequest responsibility.
package com.foodrescue.backend.surprisebox.dto
import java.util.UUID
import java.time.Instant
import java.math.BigDecimal
import com.foodrescue.backend.surprisebox.InspectionResult
import jakarta.validation.constraints.*
data class BoxRecheckRequest(@field:NotNull val result: InspectionResult?, @field:NotNull val safeUntil: Instant?,
    @field:Size(max = 2000) val notes: String? = null, val targetBatchId: UUID? = null)
