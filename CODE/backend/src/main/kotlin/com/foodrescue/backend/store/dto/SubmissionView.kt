// AC-07,13: SubmissionView responsibility.
package com.foodrescue.backend.store.dto

import java.math.BigDecimal

/** What the applicant submitted in one revision (immutable history). */
data class SubmissionView(
    val storeName: String, val address: String, val latitude: BigDecimal, val longitude: BigDecimal, val timezone: String,
    val contactPhone: String, val businessDescription: String?, val openingHours: List<OpeningHourView>
)
