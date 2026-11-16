package com.foodrescue.app.feature.staff.domain

import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.surprisebox.domain.PackedBox

internal data class HandoverWorkspace(val access: StaffAccess? = null,
    val orders: List<BoxReservation> = emptyList(), val boxes: List<PackedBox> = emptyList())
internal interface StaffHandoverRepository {
    suspend fun load(): HandoverWorkspace
    suspend fun complete(draft: HandoverDraft): BoxReservation
    suspend fun recordFailure(draft: HandoverFailureDraft): BoxReservation
}
