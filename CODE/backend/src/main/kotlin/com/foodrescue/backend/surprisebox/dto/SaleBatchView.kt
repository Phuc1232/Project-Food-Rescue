// AC-01..08 (T4.3): SaleBatchView responsibility.
package com.foodrescue.backend.surprisebox.dto

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class SaleBatchView(
    val id: UUID, val templateId: UUID, val templateName: String, val saleDate: LocalDate, val sellingPrice: BigDecimal,
    val minimumValue: BigDecimal, val orderDeadline: Instant, val pickupStartsAt: Instant, val pickupEndsAt: Instant,
    val safetyWarnings: String, val status: String, val statusReason: String?, val version: Long, val available: Long
)
