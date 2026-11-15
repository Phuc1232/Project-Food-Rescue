// AC-03,07 (T4.3): BoxCountsView responsibility.
package com.foodrescue.backend.surprisebox.dto

import java.time.Instant

/**
 * UC-62. packed = all boxes of the batch; eligible = AVAILABLE/RESERVED/ALLOCATED meeting value and safety (BR-14, BR-18);
 * available = AVAILABLE, eligible and not held (BR-15). asOf = computation time.
 */
data class BoxCountsView(
    val packed: Long, val eligible: Long, val held: Long, val allocated: Long, val delivered: Long, val blocked: Long,
    val awaitingInspection: Long, val ineligible: Long, val available: Long, val asOf: Instant
)
