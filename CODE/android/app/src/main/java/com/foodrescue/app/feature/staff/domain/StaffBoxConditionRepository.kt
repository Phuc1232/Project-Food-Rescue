package com.foodrescue.app.feature.staff.domain

import com.foodrescue.app.feature.surprisebox.domain.BoxInspectionDraft
import com.foodrescue.app.feature.surprisebox.domain.PackedBox

internal data class BoxConditionWorkspace(val access: StaffAccess? = null, val boxes: List<PackedBox> = emptyList())
internal interface StaffBoxConditionRepository {
    suspend fun load(): BoxConditionWorkspace
    suspend fun inspect(draft: BoxInspectionDraft): PackedBox
}
