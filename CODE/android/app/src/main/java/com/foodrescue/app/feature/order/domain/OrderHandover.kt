package com.foodrescue.app.feature.order.domain

import com.foodrescue.app.feature.staff.domain.*
import com.foodrescue.app.feature.surprisebox.domain.PackedBox
import com.foodrescue.app.feature.surprisebox.domain.isEligibleAt
import java.util.Locale

internal data class HandoverReceipt(val requestId: String, val boxIds: List<String>, val employeeId: String,
    val employeeName: String, val atMillis: Long)
internal enum class HandoverFailureReason { CODE_MISMATCH, BOX_UNAVAILABLE, CUSTOMER_DECLINED, OTHER }
internal data class HandoverFailure(val id: String, val reason: HandoverFailureReason, val note: String,
    val employeeId: String, val employeeName: String, val atMillis: Long)
internal data class HandoverDraft(val requestId: String, val orderId: String, val code: String = "", val boxIds: List<String> = emptyList())
internal data class HandoverFailureDraft(val id: String, val orderId: String, val reason: HandoverFailureReason? = null, val note: String = "")
internal enum class HandoverIssue { ACCESS, ORDER, PAYMENT, EARLY, ENDED, CODE, QUANTITY, BOX, REASON, NOTE, REQUEST }
internal class HandoverException(val issues: Set<HandoverIssue>) : IllegalArgumentException()

internal fun normalizePickupCode(code: String) = code.trim().uppercase(Locale.ROOT)
internal fun StaffAccess?.canHandOver(): Boolean = this?.allowed == true && permissions.containsAll(pickupPermissions)
internal fun BoxReservation.handoverAvailability(access: StaffAccess?, now: Long): Set<HandoverIssue> = buildSet {
    if (!access.canHandOver() || boxSnapshot?.storeId != access?.employee?.storeId) add(HandoverIssue.ACCESS)
    if (cancelledAtMillis != null || collectedAtMillis != null || noShow != null || ownerCancellation != null || boxSnapshot?.batchId.isNullOrBlank()) add(HandoverIssue.ORDER)
    if (!isPaid || payment?.amount != total || total <= 0) add(HandoverIssue.PAYMENT)
    if (now < pickup.startMillis) add(HandoverIssue.EARLY)
    if (now >= pickup.endMillis) add(HandoverIssue.ENDED)
}
internal fun PackedBox.canHandOverFor(order: BoxReservation, now: Long): Boolean =
    isEligibleAt(now) && storeId == order.boxSnapshot?.storeId && batchId == order.boxSnapshot.batchId &&
        expiresAt > now && packedAt <= now && items.isNotEmpty() && value >= order.boxSnapshot.minimumValue

internal fun HandoverDraft.handoverIssues(access: StaffAccess?, order: BoxReservation?, boxes: List<PackedBox>, now: Long): Set<HandoverIssue> = buildSet {
    if (requestId.isBlank()) add(HandoverIssue.REQUEST)
    if (order == null || order.id != orderId) { add(HandoverIssue.ORDER); return@buildSet }
    addAll(order.handoverAvailability(access, now))
    if (normalizePickupCode(code) != order.reference || code.isBlank()) add(HandoverIssue.CODE)
    if (boxIds.size != order.quantity || boxIds.distinct().size != boxIds.size) add(HandoverIssue.QUANTITY)
    if (boxIds.any { id -> boxes.singleOrNull { it.id == id }?.canHandOverFor(order, now) != true }) add(HandoverIssue.BOX)
}
internal fun BoxReservation.completeHandover(access: StaffAccess, draft: HandoverDraft, boxes: List<PackedBox>, now: Long): BoxReservation {
    val issues = draft.handoverIssues(access, this, boxes, now)
    if (issues.isNotEmpty()) throw HandoverException(issues)
    return copy(collectedAtMillis = now, handover = HandoverReceipt(draft.requestId, draft.boxIds.toList(), access.employee.id, access.employee.name, now))
}
internal fun BoxReservation.recordHandoverFailure(access: StaffAccess, draft: HandoverFailureDraft, now: Long): BoxReservation {
    val issues = (handoverAvailability(access, now) - HandoverIssue.ENDED).toMutableSet()
    if (id != draft.orderId) issues += HandoverIssue.ORDER
    if (draft.id.isBlank()) issues += HandoverIssue.REQUEST
    if (draft.reason == null) issues += HandoverIssue.REASON
    if (draft.note.length > 500 || (draft.reason == HandoverFailureReason.OTHER && draft.note.trim().length < 10) ||
        draft.note.any { it.isISOControl() && it != '\n' }) issues += HandoverIssue.NOTE
    if (issues.isNotEmpty()) throw HandoverException(issues)
    return copy(handoverFailures = handoverFailures + HandoverFailure(draft.id, requireNotNull(draft.reason), draft.note.trim(), access.employee.id, access.employee.name, now))
}
