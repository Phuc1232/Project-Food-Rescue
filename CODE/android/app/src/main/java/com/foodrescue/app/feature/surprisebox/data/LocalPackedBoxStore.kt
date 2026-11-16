package com.foodrescue.app.feature.surprisebox.data

import android.annotation.SuppressLint
import android.content.Context
import com.foodrescue.app.core.datastore.LocalCommerceLock
import com.foodrescue.app.feature.order.data.LocalOrderLedger
import com.foodrescue.app.feature.staff.domain.StaffAccess
import com.foodrescue.app.feature.surprisebox.domain.*

/** A single physical-box ledger is read by both owner and staff. Sale stock stays in the order ledger. */
internal class LocalPackedBoxStore(context: Context) : PackedBoxStockSource {
    private val preferences = context.applicationContext.getSharedPreferences("commerce_packed_boxes.v1", Context.MODE_PRIVATE)
    private val orders = LocalOrderLedger(context.applicationContext)
    fun load(storeId: Int): List<PackedBox> = synchronized(LocalCommerceLock) {
        val storeOrders = orders.read().map { it.order }.filter { it.boxSnapshot?.storeId == storeId }
        val allocations = storeOrders.flatMap { order ->
            order.handover?.let { receipt -> receipt.boxIds.map { it to PackedBoxHandover(order.id, order.reference, receipt.atMillis) } }.orEmpty()
        }
        check(allocations.map { it.first }.distinct().size == allocations.size) { "Duplicate physical allocation" }
        val delivered = allocations.toMap()
        val pending = storeOrders.flatMap { order -> order.noShow?.let { receipt ->
            receipt.boxIds.map { it to PackedBoxNoShow(order.id, order.reference, receipt.requestId, receipt.atMillis) }
        }.orEmpty() }
        check(pending.map { it.first }.distinct().size == pending.size) { "Duplicate No-show association" }
        val holds = pending.toMap()
        read().filter { it.storeId == storeId }.map { it.copy(handover = delivered[it.id], noShow = holds[it.id]) }
    }
    override fun packedStock(storeId: Int, batchId: String, now: Long): PackedBoxStock = synchronized(LocalCommerceLock) {
        val boxes = load(storeId).filter { it.batchId == batchId }
        PackedBoxStock(boxes.size, boxes.count { it.isEligibleAt(now) }, boxes.count { it.handover != null },
            boxes.count { it.conditionAt(now) == BoxCondition.REJECTED }, boxes.count { it.conditionAt(now) == BoxCondition.EXPIRED },
            boxes.count { it.conditionAt(now) == BoxCondition.RECHECK_REQUIRED })
    }
    @SuppressLint("UseKtx")
    fun append(box: PackedBox): PackedBox = synchronized(LocalCommerceLock) {
        val rows = read()
        rows.firstOrNull { it.id == box.id }?.let { existing ->
            check(existing == box) { "Packing request already belongs to another box" }
            return@synchronized existing
        }
        check(preferences.edit().putString("ledger", PackedBoxJson.encode(rows + box)).commit()) { "Box write failed" }
        box
    }
    // Both the write and rollback must settle before another commerce operation reads the ledger.
    @SuppressLint("UseKtx", "ApplySharedPref")
    fun inspect(access: StaffAccess, draft: BoxInspectionDraft, now: Long): PackedBox = synchronized(LocalCommerceLock) {
        if (!access.canInspectBoxes()) throw InspectionException(setOf(InspectionIssue.ACCESS))
        val box = load(access.employee.storeId).firstOrNull { it.id == draft.boxId }
            ?: throw InspectionException(setOf(InspectionIssue.BOX))
        val previous = box.inspections.indexOfFirst { it.id == draft.id }
        if (previous >= 0) {
            val event = box.inspections[previous]
            if (previous != draft.expectedRevision || event.employeeId != access.employee.id || event.result != draft.result ||
                event.reason != draft.reason || event.note != draft.note.trim() || event.noShowRequestId != draft.noShowRequestId ||
                (event.result == InspectionResult.PASSED && !(draft.foodChecked && draft.packagingChecked && draft.storageChecked && draft.allergensChecked)))
                throw InspectionException(setOf(InspectionIssue.REQUEST))
            return@synchronized box
        }
        val updated = box.inspect(access, draft, now)
        val before = preferences.getString("ledger", null)
        val rows = read().map { if (it.id == box.id) updated.copy(handover = null) else it }
        if (!preferences.edit().putString("ledger", PackedBoxJson.encode(rows)).commit()) {
            // SharedPreferences can publish to memory before disk failure. Do not recover that failed write as a receipt.
            preferences.edit().putString("ledger", before).commit()
            error("Inspection write failed")
        }
        updated
    }
    private fun read() = preferences.getString("ledger", null)?.let(PackedBoxJson::decode).orEmpty()
}
