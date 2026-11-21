package com.foodrescue.app.feature.order.domain

import com.foodrescue.app.feature.staff.domain.StaffAccess
import com.foodrescue.app.feature.surprisebox.domain.*

internal data class NoShowReceipt(val requestId: String, val boxIds: List<String>, val note: String,
    val employeeId: String, val employeeName: String, val atMillis: Long)
internal data class NoShowDraft(val requestId: String = "", val orderId: String = "", val boxIds: List<String> = emptyList(),
    val note: String = "", val customerAbsent: Boolean = false, val storeReady: Boolean = false)
internal enum class NoShowIssue { ACCESS, ORDER, PAYMENT, EARLY, INCIDENT, CHECKS, BOXES, NOTE, REQUEST }
internal class NoShowException(val issues: Set<NoShowIssue>) : IllegalArgumentException()

internal fun PackedBox.canLinkNoShow(order: BoxReservation): Boolean = storeId == order.boxSnapshot?.storeId &&
    batchId == order.boxSnapshot.batchId && packedAt <= order.pickup.endMillis && handover == null && noShow == null &&
    inspections.none { it.result == InspectionResult.REJECTED }

internal fun NoShowDraft.noShowIssues(access: StaffAccess?, order: BoxReservation?, boxes: List<PackedBox>, now: Long): Set<NoShowIssue> = buildSet {
    if (!access.canHandOver() || order?.boxSnapshot?.storeId != access?.employee?.storeId) add(NoShowIssue.ACCESS)
    if (requestId.isBlank()) add(NoShowIssue.REQUEST)
    if (order == null || order.id != orderId) { add(NoShowIssue.ORDER); return@buildSet }
    if (order.noShow != null || order.collectedAtMillis != null || order.cancelledAtMillis != null ||
        order.ownerCancellation != null || order.boxSnapshot?.batchId.isNullOrBlank()) add(NoShowIssue.ORDER)
    if (!order.isPaid || order.payment?.amount != order.total || order.total <= 0) add(NoShowIssue.PAYMENT)
    if (now < order.noShowEligibleAt) add(NoShowIssue.EARLY)
    if (order.handoverFailures.isNotEmpty()) add(NoShowIssue.INCIDENT)
    if (!customerAbsent || !storeReady) add(NoShowIssue.CHECKS)
    if (boxIds.size > order.quantity || boxIds.distinct().size != boxIds.size ||
        boxIds.any { id -> boxes.singleOrNull { it.id == id }?.canLinkNoShow(order) != true }) add(NoShowIssue.BOXES)
    if (note.length > 500 || note.any { it.isISOControl() && it != '\n' }) add(NoShowIssue.NOTE)
}

internal fun BoxReservation.confirmNoShow(access: StaffAccess, draft: NoShowDraft, boxes: List<PackedBox>, now: Long): BoxReservation {
    val issues = draft.noShowIssues(access, this, boxes, now)
    if (issues.isNotEmpty()) throw NoShowException(issues)
    return copy(noShow = NoShowReceipt(draft.requestId, draft.boxIds.toList(), draft.note.trim(), access.employee.id, access.employee.name, now))
}

internal val BoxReservation.noShowEligibleAt: Long get() = pickup.endMillis + (financialTerms?.noShowGraceMinutes ?: 0) * 60_000L
