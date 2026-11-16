package com.foodrescue.app.feature.staff.data

import android.content.Context
import com.foodrescue.app.feature.staff.domain.*
import com.foodrescue.app.feature.menu.data.LocalMenuAssetStore
import com.foodrescue.app.feature.menu.data.LocalMenuRepository
import com.foodrescue.app.feature.order.data.LocalOrderLedger
import com.foodrescue.app.feature.store.data.LocalOwnerStoreRepository
import com.foodrescue.app.feature.surprisebox.data.LocalOwnerBoxRepository
import com.foodrescue.app.feature.surprisebox.data.LocalPackedBoxStore
import com.foodrescue.app.feature.surprisebox.domain.*

internal class LocalStaffPackingRepository(context: Context, private val email: String,
    private val directory: LocalEmployeeDirectory, private val clock: () -> Long = System::currentTimeMillis) : StaffPackingRepository {
    private val context = context.applicationContext
    private val inventory = LocalPackedBoxStore(this.context)
    override suspend fun load(): StaffWorkspace = directory.withMembership(email) { ownerEmail, access ->
        if (ownerEmail == null || access?.allowed != true || access.permissions.none {
                it == EmployeePermission.PREPARE_BOXES || it == EmployeePermission.HAND_OVER })
            return@withMembership StaffWorkspace(access)
        val owner = LocalOwnerStoreRepository(context, ownerEmail)
        val menu = LocalMenuRepository(context, ownerEmail, owner, LocalMenuAssetStore(context, ownerEmail))
        val boxes = LocalOwnerBoxRepository(context, ownerEmail, owner, LocalOrderLedger(context), access.employee.storeId, inventory, clock)
        menu.withCurrentMenu { current -> boxes.withCurrentCatalog { catalog ->
            StaffWorkspace(access, current.items, catalog.batches, inventory.load(access.employee.storeId))
        } }
    }
    override suspend fun complete(draft: PackingDraft): PackedBox = directory.withMembership(email) { ownerEmail, access ->
        if (ownerEmail == null || access?.allowed != true || EmployeePermission.PREPARE_BOXES !in access.permissions)
            throw PackingException(setOf(PackingIssue.ACCESS))
        val owner = LocalOwnerStoreRepository(context, ownerEmail)
        val menu = LocalMenuRepository(context, ownerEmail, owner, LocalMenuAssetStore(context, ownerEmail))
        val boxes = LocalOwnerBoxRepository(context, ownerEmail, owner, LocalOrderLedger(context), access.employee.storeId, inventory, clock)
        // One lock order: membership -> menu -> batch -> commerce. No mutable source can change between validation and commit.
        menu.withCurrentMenu { current -> boxes.withCurrentCatalog { catalog ->
            val previous = inventory.load(access.employee.storeId).firstOrNull { it.id == draft.id }
            if (previous != null) {
                if (previous.preparedById != access.employee.id || previous.batchId != draft.batchId)
                    throw PackingException(setOf(PackingIssue.ACCESS))
                previous
            } else {
                val batch = catalog.batches.firstOrNull { it.id == draft.batchId } ?: throw PackingException(setOf(PackingIssue.BATCH))
                inventory.append(draft.completePacking(access, batch, current.items, clock()))
            }
        } }
    }
}
