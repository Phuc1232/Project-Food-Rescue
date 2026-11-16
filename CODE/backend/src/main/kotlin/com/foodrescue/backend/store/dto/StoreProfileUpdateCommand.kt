// AC-02,03: StoreProfileUpdateCommand responsibility.
package com.foodrescue.backend.store.dto

/** Parsed PATCH body; descriptionPresent distinguishes "not sent" from "clear". */
data class StoreProfileUpdateCommand(
    val version: Long, val descriptionPresent: Boolean, val description: String?, val openingHours: List<OpeningHourRequest>?
)
