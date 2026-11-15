// AC-06 (T5.1): StorePin responsibility.
package com.foodrescue.backend.search.dto

import java.math.BigDecimal
import java.util.UUID

data class StorePin(val id: UUID, val name: String, val latitude: BigDecimal, val longitude: BigDecimal, val hasOpenOffers: Boolean)
