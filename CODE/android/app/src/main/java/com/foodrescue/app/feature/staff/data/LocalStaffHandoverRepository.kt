package com.foodrescue.app.feature.staff.data

import android.content.Context
import com.foodrescue.app.core.datastore.LocalCommerceLock
import com.foodrescue.app.feature.order.data.LocalOrderLedger
import com.foodrescue.app.feature.order.domain.*
import com.foodrescue.app.feature.staff.domain.*
import com.foodrescue.app.feature.surprisebox.data.LocalPackedBoxStore

internal class LocalStaffHandoverRepository(context: Context, private val email: String,
    private val directory: LocalEmployeeDirectory, private val clock: () -> Long = System::currentTimeMillis) : StaffHandoverRepository {
    private val ledger = LocalOrderLedger(context.applicationContext)
    private val inventory = LocalPackedBoxStore(context.applicationContext)
    override suspend fun load(): HandoverWorkspace = directory.withMembership(email) { _, access ->
        if (access?.allowed != true || EmployeePermission.VIEW_ORDERS !in access.permissions) return@withMembership HandoverWorkspace(access)
        synchronized(LocalCommerceLock) {
            HandoverWorkspace(access, ledger.read().map { it.order }.filter { it.boxSnapshot?.storeId == access.employee.storeId },
                if (access.canHandOver()) inventory.load(access.employee.storeId) else emptyList())
        }
    }
    override suspend fun complete(draft: HandoverDraft): BoxReservation = change(draft.orderId) { access, order ->
        val previous = order.handover
        if (previous?.requestId == draft.requestId) {
            if (previous.employeeId != access.employee.id || previous.boxIds != draft.boxIds || normalizePickupCode(draft.code) != order.reference)
                throw HandoverException(setOf(HandoverIssue.REQUEST))
            order
        } else order.completeHandover(access, draft, inventory.load(access.employee.storeId), clock())
    }
    override suspend fun recordFailure(draft: HandoverFailureDraft): BoxReservation = change(draft.orderId) { access, order ->
        val previous = order.handoverFailures.firstOrNull { it.id == draft.id }
        if (previous != null) {
            if (previous.employeeId != access.employee.id || previous.reason != draft.reason || previous.note != draft.note.trim())
                throw HandoverException(setOf(HandoverIssue.REQUEST))
            order
        } else order.recordHandoverFailure(access, draft, clock())
    }
    // One commit owns both the terminal order state and allocation of physical boxes.
    private suspend fun change(orderId: String, transform: (StaffAccess, BoxReservation) -> BoxReservation): BoxReservation =
        directory.withMembership(email) { _, access ->
            if (access == null || !access.canHandOver()) throw HandoverException(setOf(HandoverIssue.ACCESS))
            synchronized(LocalCommerceLock) {
                val rows = ledger.read()
                val index = rows.indexOfFirst { it.order.id == orderId && it.order.boxSnapshot?.storeId == access.employee.storeId }
                if (index < 0) throw HandoverException(setOf(HandoverIssue.ORDER))
                val next = transform(access, rows[index].order)
                if (next != rows[index].order) ledger.write(rows.toMutableList().apply { set(index, rows[index].copy(order = next)) })
                next
            }
        }
}
