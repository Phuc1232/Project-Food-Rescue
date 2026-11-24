// AC-01..14 (T7.4): AwaitingInspectionBoxView responsibility.
package com.foodrescue.backend.surprisebox.dto
import java.util.UUID
import java.time.Instant
import java.math.BigDecimal
data class AwaitingInspectionBoxView(val box: BoxView, val latestInspection: BoxInspectionView?,
    val sourceOrderId: UUID, val sourceOrderCode: String, val noShowCaseId: UUID, val noShowVerifiedAt: Instant)
