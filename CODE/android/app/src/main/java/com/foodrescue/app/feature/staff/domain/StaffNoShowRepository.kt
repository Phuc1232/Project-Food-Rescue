package com.foodrescue.app.feature.staff.domain

import com.foodrescue.app.feature.order.domain.BoxReservation
import com.foodrescue.app.feature.order.domain.NoShowDraft

internal interface StaffNoShowRepository {
    suspend fun load(): HandoverWorkspace
    suspend fun confirm(draft: NoShowDraft): BoxReservation
}
