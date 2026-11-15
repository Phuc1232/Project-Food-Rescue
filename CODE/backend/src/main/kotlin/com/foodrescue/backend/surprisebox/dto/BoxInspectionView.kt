// AC-01,06,08 (T4.4): BoxInspectionView responsibility.
package com.foodrescue.backend.surprisebox.dto

import java.time.Instant
import java.util.UUID

data class BoxInspectionView(val result: String, val inspectorId: UUID, val inspectedAt: Instant, val safeUntil: Instant, val notes: String)
