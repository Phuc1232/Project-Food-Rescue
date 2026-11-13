// AC-01,07,13: OpeningHourView responsibility.
package com.foodrescue.backend.store.dto

/** Times are local to the store timezone, formatted HH:mm. */
data class OpeningHourView(val dayOfWeek: Int, val shiftNumber: Int, val opensAt: String, val closesAt: String, val closesNextDay: Boolean)
