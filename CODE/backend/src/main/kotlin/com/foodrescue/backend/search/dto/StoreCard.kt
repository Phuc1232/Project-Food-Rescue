// AC-06 (T5.1): StoreCard responsibility.
package com.foodrescue.backend.search.dto

import java.math.BigDecimal
import java.util.UUID

data class StoreCard(val id: UUID, val name: String, val address: String, val latitude: BigDecimal, val longitude: BigDecimal,
                     val distanceKm: Double?, val openOffers: Long, val directionsUrl: String)
