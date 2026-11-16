package com.foodrescue.app.feature.staff.domain

import com.foodrescue.app.feature.menu.domain.MenuItem
import com.foodrescue.app.feature.surprisebox.domain.*

internal data class StaffWorkspace(val access: StaffAccess? = null, val menu: List<MenuItem> = emptyList(),
    val batches: List<SaleBatch> = emptyList(), val boxes: List<PackedBox> = emptyList())
internal interface StaffPackingRepository {
    suspend fun load(): StaffWorkspace
    suspend fun complete(draft: PackingDraft): PackedBox
}
