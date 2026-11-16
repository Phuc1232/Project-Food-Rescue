package com.foodrescue.app.feature.surprisebox.domain

import com.foodrescue.app.feature.staff.domain.EmployeePermission
import com.foodrescue.app.feature.staff.domain.StaffAccess

internal enum class BoxCondition { READY, RECHECK_REQUIRED, REJECTED, EXPIRED, DELIVERED }
internal enum class InspectionResult { PASSED, REJECTED }
internal enum class InspectionReason { FOOD_QUALITY, PACKAGING, STORAGE, ALLERGENS, EXPIRED, OTHER }
internal enum class InspectionIssue { ACCESS, BOX, STALE, REQUEST, RESULT, REASON, NOTE, CHECKS, EXPIRY }
internal class InspectionException(val issues: Set<InspectionIssue>) : IllegalArgumentException()

/** Append-only evidence; it never rewrites the original packing receipt. */
internal data class BoxInspection(val id: String, val result: InspectionResult, val reason: InspectionReason?,
    val note: String, val employeeId: String, val employeeName: String, val atMillis: Long, val noShowRequestId: String? = null)
internal data class BoxInspectionDraft(val id: String = "", val boxId: String = "", val expectedRevision: Int = 0,
    val result: InspectionResult? = null, val reason: InspectionReason? = null, val note: String = "",
    val foodChecked: Boolean = false, val packagingChecked: Boolean = false,
    val storageChecked: Boolean = false, val allergensChecked: Boolean = false, val noShowRequestId: String? = null)

internal fun StaffAccess?.canInspectBoxes(): Boolean = this?.allowed == true &&
    permissions.any { it == EmployeePermission.PREPARE_BOXES || it == EmployeePermission.HAND_OVER }
internal fun PackedBox.conditionAt(now: Long): BoxCondition = when {
    handover != null -> BoxCondition.DELIVERED
    inspections.any { it.result == InspectionResult.REJECTED } -> BoxCondition.REJECTED
    now >= expiresAt -> BoxCondition.EXPIRED
    needsNoShowInspection() -> BoxCondition.RECHECK_REQUIRED
    else -> BoxCondition.READY
}
internal fun PackedBox.isEligibleAt(now: Long): Boolean = conditionAt(now) == BoxCondition.READY &&
    packedAt <= now && items.isNotEmpty() && value >= minimumValue
internal fun PackedBox.canInspect(): Boolean = handover == null && inspections.none { it.result == InspectionResult.REJECTED }
internal fun PackedBox.needsNoShowInspection(): Boolean = noShow?.let { hold ->
    inspections.none { it.noShowRequestId == hold.requestId && it.atMillis >= hold.atMillis }
} == true

internal fun BoxInspectionDraft.inspectionIssues(access: StaffAccess?, box: PackedBox?, now: Long): Set<InspectionIssue> = buildSet {
    if (!access.canInspectBoxes() || box?.storeId != access?.employee?.storeId) add(InspectionIssue.ACCESS)
    if (box == null || box.id != boxId || !box.canInspect() || now < box.packedAt ||
        now < (box.inspections.lastOrNull()?.atMillis ?: 0)) add(InspectionIssue.BOX)
    if (box != null && box.inspections.size != expectedRevision) add(InspectionIssue.STALE)
    if (box != null && (box.noShow?.requestId != noShowRequestId || now < (box.noShow?.atMillis ?: 0))) add(InspectionIssue.STALE)
    if (id.isBlank() || box?.inspections?.any { it.id == id } == true) add(InspectionIssue.REQUEST)
    if (result == null) add(InspectionIssue.RESULT)
    if (result == InspectionResult.REJECTED && (reason == null || (reason == InspectionReason.EXPIRED && box != null && now < box.expiresAt))) add(InspectionIssue.REASON)
    if (result == InspectionResult.PASSED) {
        if (reason != null) add(InspectionIssue.REASON)
        if (!foodChecked || !packagingChecked || !storageChecked || !allergensChecked) add(InspectionIssue.CHECKS)
        if (box == null || now >= box.expiresAt) add(InspectionIssue.EXPIRY)
    }
    if (note.length > 500 || (reason == InspectionReason.OTHER && note.trim().length < 10) ||
        note.any { it.isISOControl() && it != '\n' }) add(InspectionIssue.NOTE)
}
internal fun PackedBox.inspect(access: StaffAccess, draft: BoxInspectionDraft, now: Long): PackedBox {
    val issues = draft.inspectionIssues(access, this, now)
    if (issues.isNotEmpty()) throw InspectionException(issues)
    return copy(inspections = inspections + BoxInspection(draft.id, checkNotNull(draft.result), draft.reason,
        draft.note.trim(), access.employee.id, access.employee.name, now, draft.noShowRequestId))
}
