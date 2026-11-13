// AC-07,08,13: ApplicationSnapshot responsibility.
package com.foodrescue.backend.store.service

import com.foodrescue.backend.store.dto.OpeningHourView
import java.math.BigDecimal
import java.util.UUID

/** JSON stored in partner_application.business_details: the full submission of one revision (T3.2 Q5, Q6). */
data class ApplicationSnapshot(
    val storeName: String, val address: String, val latitude: BigDecimal, val longitude: BigDecimal, val timezone: String,
    val contactPhone: String, val businessDescription: String?, val openingHours: List<OpeningHourView>,
    val documents: List<SnapshotDocument>
)

data class SnapshotDocument(val type: String, val fileId: UUID)
