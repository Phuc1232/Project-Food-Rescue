// AC-01,02: StoreProfileView responsibility.
package com.foodrescue.backend.store.dto

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Owner/admin view of a store; send version back with PATCH .../profile. */
data class StoreProfileView(
    val id: UUID, val name: String, val description: String?, val address: String, val latitude: BigDecimal,
    val longitude: BigDecimal, val timezone: String, val status: String, val statusReason: String?, val statusChangedAt: Instant?,
    val version: Long, val openingHours: List<OpeningHourView>, val pendingChangeRequest: ChangeRequestView?
)
