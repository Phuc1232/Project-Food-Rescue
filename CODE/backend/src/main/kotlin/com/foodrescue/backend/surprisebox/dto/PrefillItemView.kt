// AC-07 (T4.4): PrefillItemView responsibility.
package com.foodrescue.backend.surprisebox.dto

import java.math.BigDecimal
import java.util.UUID

/** Quick-fill line from a packed box: item and quantity only, with the item's CURRENT price and status (BR-12). */
data class PrefillItemView(val menuItemId: UUID, val name: String, val quantity: Int, val currentUnitPrice: BigDecimal, val status: String)
