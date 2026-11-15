// AC-03,09 (T3.4): MyAssignmentView responsibility.
package com.foodrescue.backend.staff.dto

import com.foodrescue.backend.store.dto.StoreSummary
import java.time.Instant
import java.util.UUID

data class MyAssignmentView(val assignmentId: UUID, val store: StoreSummary, val status: String, val permissions: List<String>,
                            val assignedAt: Instant)
