package com.foodrescue.app.feature.staff.data

import android.content.Context
import com.foodrescue.app.core.datastore.LocalCommerceLock
import com.foodrescue.app.feature.order.data.LocalOrderLedger
import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.staff.domain.*
import com.foodrescue.app.feature.surprisebox.data.LocalPackedBoxStore

internal class LocalStaffNoShowRepository(context: Context, private val email: String,
    private val directory: LocalEmployeeDirectory, private val clock: () -> Long = System::currentTimeMillis) : StaffNoShowRepository {
    private val ledger = LocalOrderLedger(context.applicationContext)
    private val inventory = LocalPackedBoxStore(context.applicationContext)
    private val handover = LocalStaffHandoverRepository(context, email, directory, clock)
    override suspend fun load() = handover.load()
    override suspend fun confirm(draft: NoShowDraft): BoxReservation = directory.withMembership(email) { _, access ->
        if (access == null || !access.canHandOver()) throw NoShowException(setOf(NoShowIssue.ACCESS))
        synchronized(LocalCommerceLock) {
            val rows = ledger.read()
            val index = rows.indexOfFirst { it.order.id == draft.orderId && it.order.boxSnapshot?.storeId == access.employee.storeId }
            if (index < 0) throw NoShowException(setOf(NoShowIssue.ORDER))
            val order = rows[index].order
            val previous = order.noShow
            if (previous?.requestId == draft.requestId) {
                if (previous.employeeId != access.employee.id || previous.boxIds != draft.boxIds || previous.note != draft.note.trim() ||
                    !draft.customerAbsent || !draft.storeReady) throw NoShowException(setOf(NoShowIssue.REQUEST))
                return@synchronized order
            }
            if (rows.any { it.order.noShow?.requestId == draft.requestId }) throw NoShowException(setOf(NoShowIssue.REQUEST))
            val next = order.confirmNoShow(access, draft, inventory.load(access.employee.storeId), clock())
            // The receipt is also the source of physical inspection holds: one atomic write.
            ledger.write(rows.toMutableList().apply { set(index, rows[index].copy(order = next)) })
            next
        }
    }
}
