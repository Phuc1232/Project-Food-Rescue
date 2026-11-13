// AC-07,10: StoreView responsibility.
package com.foodrescue.backend.store.dto

import java.math.BigDecimal
import java.util.UUID

/** Current store data (the latest submitted revision is applied to it). */
data class StoreView(
    val id: UUID, val status: String, val name: String, val address: String, val latitude: BigDecimal, val longitude: BigDecimal,
    val timezone: String, val openingHours: List<OpeningHourView>
)
