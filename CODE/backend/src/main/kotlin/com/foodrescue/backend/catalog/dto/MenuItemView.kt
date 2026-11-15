// AC-01,07,08 (T4.1): MenuItemView responsibility.
package com.foodrescue.backend.catalog.dto

import java.math.BigDecimal
import java.util.UUID

/** listedPrice is the store's normal price (BR-04), whole VND. */
data class MenuItemView(val id: UUID, val name: String, val categoryCode: String, val categoryName: String, val listedPrice: BigDecimal,
                        val ingredients: String, val safetyWarnings: String, val status: String, val version: Long)
