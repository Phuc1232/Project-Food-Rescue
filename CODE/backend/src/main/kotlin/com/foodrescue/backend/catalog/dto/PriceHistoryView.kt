// AC-03,08 (T4.1): PriceHistoryView responsibility.
package com.foodrescue.backend.catalog.dto

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class PriceHistoryView(val previousPrice: BigDecimal, val newPrice: BigDecimal, val changedAt: Instant, val changedById: UUID, val reason: String)
