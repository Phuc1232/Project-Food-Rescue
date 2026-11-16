// AC-01..06 (T4.2): BoxTemplateView responsibility.
package com.foodrescue.backend.surprisebox.dto

import java.math.BigDecimal
import java.util.UUID

data class BoxTemplateView(
    val id: UUID, val name: String, val categoryCode: String, val categoryName: String, val description: String, val safetyWarnings: String,
    val defaultSellingPrice: BigDecimal, val defaultMinimumValue: BigDecimal, val coverFileId: UUID?, val active: Boolean, val version: Long
)
