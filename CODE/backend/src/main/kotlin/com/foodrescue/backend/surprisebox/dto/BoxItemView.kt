// AC-01,05,08 (T4.4): BoxItemView responsibility.
package com.foodrescue.backend.surprisebox.dto

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Prices and names as recorded when the box was packed (BR-07). */
data class BoxItemView(val menuItemId: UUID, val name: String, val quantity: Int, val unitPrice: BigDecimal, val safetyWarnings: String, val expiresAt: Instant)
