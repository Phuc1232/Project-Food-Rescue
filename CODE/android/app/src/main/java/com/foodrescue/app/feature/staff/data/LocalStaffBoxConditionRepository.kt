package com.foodrescue.app.feature.staff.data

import android.content.Context
import com.foodrescue.app.feature.staff.domain.*
import com.foodrescue.app.feature.surprisebox.data.LocalPackedBoxStore
import com.foodrescue.app.feature.surprisebox.domain.*

internal class LocalStaffBoxConditionRepository(context: Context, private val email: String,
    private val directory: LocalEmployeeDirectory, private val clock: () -> Long = System::currentTimeMillis) : StaffBoxConditionRepository {
    private val inventory = LocalPackedBoxStore(context.applicationContext)
    override suspend fun load(): BoxConditionWorkspace = directory.withMembership(email) { _, access ->
        BoxConditionWorkspace(access, if (access.canInspectBoxes()) inventory.load(checkNotNull(access).employee.storeId) else emptyList())
    }
    override suspend fun inspect(draft: BoxInspectionDraft): PackedBox = directory.withMembership(email) { _, access ->
        if (access == null || !access.canInspectBoxes()) throw InspectionException(setOf(InspectionIssue.ACCESS))
        // Membership -> commerce is the same lock order as handover and replacement packing.
        inventory.inspect(access, draft, clock())
    }
}
