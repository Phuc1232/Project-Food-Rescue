// AC-12: StorePublicView responsibility.
package com.foodrescue.backend.store.dto

import java.math.BigDecimal
import java.util.UUID

/** What customers see of an ACTIVE store; no owner, status reason or version. */
data class StorePublicView(
    val id: UUID, val name: String, val description: String?, val address: String, val latitude: BigDecimal,
    val longitude: BigDecimal, val timezone: String, val openingHours: List<OpeningHourView>
)
